data "aws_s3_bucket" "ebsco_adapter" {
  bucket = "wellcomecollection-platform-ebsco-adapter"
}
module "trigger_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-trigger"
  description = "Lambda function to trigger EBSCO adapter ingestion"
  runtime     = "python3.12"
  publish     = true

  filename = data.archive_file.empty_zip.output_path

  handler     = "steps.trigger.lambda_handler"
  memory_size = 1024
  timeout     = 300

  environment = {
    variables = {
      S3_BUCKET = data.aws_s3_bucket.ebsco_adapter.id
      S3_PREFIX = "prod"
    }
  }
}

resource "aws_iam_role_policy" "trigger_lambda_ssm_read" {
  role   = module.trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ssm_read.json
}

# IAM policies for EBSCO adapter S3 bucket
resource "aws_iam_role_policy" "trigger_lambda_s3_read" {
  role   = module.trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_read.json
}

resource "aws_iam_role_policy" "trigger_lambda_s3_write" {
  role   = module.trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_write.json
}