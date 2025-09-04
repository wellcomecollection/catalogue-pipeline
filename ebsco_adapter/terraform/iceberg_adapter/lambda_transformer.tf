module "transformer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-transformer"
  description = "Lambda function to transform EBSCO data from loader output"
  runtime     = "python3.12"
  publish     = true

  filename = data.archive_file.empty_zip.output_path

  handler     = "steps.transformer.lambda_handler"
  memory_size = 4096
  timeout     = 600

  environment = {
    variables = {
      PIPELINE_DATE = local.pipeline_date
      INDEX_DATE    = local.index_date
    }
  }
}

# Attach read-only Iceberg access policy to transformer lambda
resource "aws_iam_role_policy" "transformer_lambda_iceberg_write" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.iceberg_write.json
}

# Attach S3 read policy (same as loader) to transformer lambda
resource "aws_iam_role_policy" "transformer_lambda_s3_read" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_read.json
}

# Attach S3 write policy (same as loader) to transformer lambda
resource "aws_iam_role_policy" "transformer_lambda_s3_write" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_write.json
}

# Allow transformer to read pipeline storage secrets
resource "aws_iam_role_policy" "transformer_lambda_pipeline_storage_secret_read" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.transformer_allow_pipeline_storage_secret_read.json
}
