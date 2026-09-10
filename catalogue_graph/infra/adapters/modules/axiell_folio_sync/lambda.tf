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

  # Attached to the VPC only for the dev target. Prod is public SaaS and needs no
  # VPC. See docs/axiell-folio-sync-lambda-dev-instance.md.
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
        # Used when an event does not name a target. The event still wins.
        FOLIO_TARGET = var.folio_default_target
      },
      # Set only for the dev target, so a folio_target="dev" run without it fails
      # rather than using the prod credentials.
      var.folio_dev_target_enabled ? {
        OKAPI_DEV_SECRET_PARAM = aws_ssm_parameter.okapi_credentials_dev[0].name
      } : {},
    )
  }
}
