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
