module "reporter_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-ingestor-reporter"
  description = "Generates a report on the latest pipeline run and posts it to #wc-search-alerts"
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "ingestor_reporter.lambda_handler"
  memory_size = 128
  timeout     = 300

  environment = {
    variables = {
      INGESTOR_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX = "ingestor"
      SLACK_SECRET_ID = local.slack_webhook
    }
  }
}

resource "aws_iam_role_policy" "reporter_lambda_read_slack_secret_policy" {
  role   = module.reporter_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_slack_secret_read.json
}

resource "aws_iam_role_policy" "reporter_lambda_s3_read_policy" {
  role   = module.reporter_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}