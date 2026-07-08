module "mark_published_lambda" {
  count  = local.published_tracking ? 1 : 0
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${var.namespace}-adapter-mark-published"
  description  = "Lambda function to stamp harvested windows as published"
  package_type = "Image"
  image_uri    = "${var.repository_url}:prod"
  # CI deploys via `update-function-code --publish` and nothing consumes a
  # versioned ARN, so Terraform publishing only caused a perpetual version diff.
  publish = false

  image_config = {
    command = ["adapters.steps.${local.steps_namespace}.mark_published.lambda_handler"]
  }

  memory_size = 4096
  timeout     = 600

  ephemeral_storage = {
    size = 1024
  }

  environment = {
    variables = {
      S3_BUCKET = data.aws_s3_bucket.adapter.id
      S3_PREFIX = "prod"
    }
  }
}

resource "aws_iam_role_policy" "mark_published_lambda_iceberg_write" {
  count  = local.published_tracking ? 1 : 0
  role   = one(module.mark_published_lambda[*].lambda_role.name)
  policy = data.aws_iam_policy_document.iceberg_write.json
}
