# The sync runs out of the shared unified_pipeline_lambda image with the
# entrypoint overridden, deployed via the standard terraform-aws-lambda module
# (same convention as the adapter trigger lambdas). Build/deploy with
# scripts/deploy_lambda.sh axiell-folio-sync.
module "sync_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = var.namespace
  description  = "Axiell → FOLIO outbound sync (upserts changed records into FOLIO Inventory)"
  package_type = "Image"
  image_uri    = "${var.repository_url}:${var.image_tag}"
  # CI deploys via `update-function-code --publish`; nothing consumes a versioned
  # ARN, so Terraform publishing only causes a perpetual version diff.
  publish = false

  image_config = {
    command = ["adapters.steps.axiell_folio_sync.lambda_handler"]
  }

  memory_size = var.lambda_memory_mb
  timeout     = var.lambda_timeout_seconds

  environment = {
    variables = {
      OKAPI_URL          = var.okapi_url
      OKAPI_TENANT       = var.okapi_tenant
      OKAPI_SECRET_PARAM = aws_ssm_parameter.okapi_credentials.name
      MANIFEST_S3_BUCKET = aws_s3_bucket.axiell_folio_sync_manifests.bucket
      # The adapter table is read via AXIELL_CONFIG/AdapterStore, so no
      # Iceberg-specific env vars are needed here.
      DRY_RUN = tostring(var.dry_run_default)
    }
  }
}
