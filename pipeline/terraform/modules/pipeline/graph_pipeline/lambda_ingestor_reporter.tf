module "ingestor_reporter_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${local.namespace}-ingestor-reporter-${var.pipeline_date}"
  description  = "Generates a report on the latest pipeline run and posts it to #wc-search-alerts"
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`

  image_config = {
    command = ["ingestor.steps.ingestor_reporter.lambda_handler"]
  }

  memory_size = 128
  timeout     = 300

  environment = {
    variables = {
      CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX        = "ingestor"
      SLACK_SECRET_ID           = local.slack_webhook
    }
  }
}

resource "aws_iam_role_policy" "ingestor_reporter_lambda_read_slack_secret_policy" {
  role   = module.ingestor_reporter_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_slack_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_reporter_lambda_s3_read_policy" {
  role   = module.ingestor_reporter_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}
