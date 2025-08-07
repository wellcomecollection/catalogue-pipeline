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
}
