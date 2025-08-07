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

# IAM policy for writing to Iceberg table
resource "aws_iam_role_policy" "loader_lambda_iceberg_write" {
  role   = module.loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.iceberg_write.json
}

# IAM policy for reading from EBSCO adapter S3 bucket
resource "aws_iam_role_policy" "loader_lambda_s3_read" {
  role   = module.loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_read.json
}
