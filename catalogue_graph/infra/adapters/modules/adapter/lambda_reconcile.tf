module "reconcile_lambda" {
  count  = var.enable_reconciliation ? 1 : 0
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${var.namespace}-adapter-reconcile"
  description  = "Lambda function to record guid-change deletion facts before the completed event fires"
  package_type = "Image"
  image_uri    = "${var.repository_url}:prod"
  # CI deploys via `update-function-code --publish` and nothing consumes a
  # versioned ARN, so Terraform publishing only caused a perpetual version diff.
  publish = false

  image_config = {
    command = ["adapters.steps.${local.steps_namespace}.reconcile.lambda_handler"]
  }

  # The changeset read of the id-sorted adapter store cannot prune and can
  # materialise most of the table (#3444); sized to match the pipeline
  # transformer lambda, which performs the same read.
  memory_size = 10240
  timeout     = 600

  ephemeral_storage = {
    size = 1024
  }
}

resource "aws_iam_role_policy" "reconcile_lambda_iceberg_write" {
  count  = var.enable_reconciliation ? 1 : 0
  role   = one(module.reconcile_lambda[*].lambda_role.name)
  policy = data.aws_iam_policy_document.iceberg_write.json
}
