module "loader_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "folio-adapter-loader"
  description  = "Lambda function to load FOLIO data into Iceberg table"
  package_type = "Image"
  image_uri    = "${var.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["adapters.folio.steps.loader.lambda_handler"]
  }

  memory_size = 10240
  timeout     = 900
}

# IAM policy for writing to Iceberg table
resource "aws_iam_role_policy" "loader_lambda_iceberg_write" {
  role   = module.loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.iceberg_write.json
}

# IAM policy for reading from FOLIO adapter S3 bucket
resource "aws_iam_role_policy" "loader_lambda_s3_read" {
  role   = module.loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_read.json
}

resource "aws_iam_role_policy" "loader_lambda_s3_write" {
  role   = module.loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_write.json
}

resource "aws_iam_role_policy" "loader_lambda_ssm_read" {
  role   = module.loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ssm_read.json
}

resource "aws_iam_role_policy" "loader_lambda_cloudwatch_put_metric" {
  role   = module.loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.cloudwatch_put_metric_data.json
}
