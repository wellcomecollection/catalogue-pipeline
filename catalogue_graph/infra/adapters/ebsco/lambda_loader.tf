module "loader_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-loader"
  description = "Lambda function to load EBSCO data into Iceberg table"
  package_type = "Image"
  image_uri    = "${var.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["adapters.ebsco.steps.loader.lambda_handler"]
  }

  memory_size = 10240
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

resource "aws_iam_role_policy" "loader_lambda_s3_write" {
  role   = module.loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_write.json
}

# Create the Glue database that the Lambda will use
resource "aws_glue_catalog_database" "wellcomecollection_catalogue" {
  name        = "wellcomecollection_catalogue"
  description = "Database for Wellcome Collection catalogue data from EBSCO adapter"
}