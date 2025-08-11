
data "archive_file" "empty_zip" {
  output_path = "data/empty.zip"
  type        = "zip"
  source {
    content  = "// This file is intentionally left empty"
    filename = "lambda.py"
  }
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
        S3_BUCKET = resource.aws_s3_bucket.ebsco_adapter.bucket
        S3_PREFIX = "prod"
      }
  }
}

module "loader_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-loader"
  description = "Lambda function to load EBSCO data into Iceberg table"
  runtime     = "python3.12"
  publish     = true

  filename = data.archive_file.empty_zip.output_path

  handler     = "steps.loader.lambda_handler"
  memory_size = 2048
  timeout     = 900
}

module "transformer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-transformer"
  description = "Lambda function to transform EBSCO data from loader output"
  runtime     = "python3.12"
  publish     = true

  filename = data.archive_file.empty_zip.output_path

  handler     = "steps.transformer.lambda_handler"
  memory_size = 1024
  timeout     = 600
}
