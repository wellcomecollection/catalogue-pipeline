# The sync runs out of the shared unified_pipeline_lambda image with the
# entrypoint overridden, deployed via the standard terraform-aws-lambda module
# (same convention as the adapter trigger lambdas). Build/deploy with
# scripts/deploy_lambda.sh axiell-folio-sync-adapter-lambda.
module "sync_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${var.namespace}-adapter-lambda"
  description  = "Axiell to FOLIO outbound sync (upserts changed records into FOLIO Inventory)"
  package_type = "Image"
  image_uri    = "${var.repository_url}:dev"
  # CI deploys via `update-function-code --publish`; nothing consumes a versioned
  # ARN, so Terraform publishing only causes a perpetual version diff.
  publish = false

  image_config = {
    command = ["adapters.steps.axiell_folio_sync.axiell_folio_sync.lambda_handler"]
  }

  memory_size = var.lambda_memory_mb
  timeout     = var.lambda_timeout_seconds

  # The FOLIO dev server has no public endpoint, so dev-target runs need the ENIs
  # in its VPC. Attaching drops the Lambda's default internet egress, which is why
  # the subnets must have NAT plus the S3 gateway endpoint (see
  # docs/axiell-folio-sync-lambda-dev-instance.md). Left null for prod: the EBSCO
  # SaaS target is on the public internet and needs no VPC at all.
  vpc_config = var.folio_dev_target_enabled ? {
    subnet_ids         = var.folio_dev_subnets
    security_group_ids = var.folio_dev_security_group_ids
  } : null

  environment = {
    variables = merge(
      {
        # url/tenant/username/password all come from this SSM SecureString.
        OKAPI_SECRET_PARAM = aws_ssm_parameter.okapi_credentials.name
        MANIFEST_S3_BUCKET = aws_s3_bucket.axiell_folio_sync_manifests.bucket
        # The adapter table is read via AXIELL_CONFIG/AdapterStore, so no
        # Iceberg-specific env vars are needed here.
        DRY_RUN = tostring(var.dry_run_default)
        # The fallback target for runs whose event does not name one. An event
        # carrying folio_target still overrides this.
        FOLIO_TARGET = var.folio_default_target
      },
      # Only present when the dev target is enabled; without it a folio_target="dev"
      # event fails loudly rather than falling back to the prod credentials.
      var.folio_dev_target_enabled ? {
        OKAPI_DEV_SECRET_PARAM = aws_ssm_parameter.okapi_credentials_dev[0].name
      } : {},
    )
  }
}
