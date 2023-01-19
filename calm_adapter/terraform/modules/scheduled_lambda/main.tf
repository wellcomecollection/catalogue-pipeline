module "scheduled_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda.git?ref=v1.1.1"

  name        = var.name
  description = var.description

  handler = "lambda.main"
  runtime = "python3.7"
  timeout = var.timeout

  s3_bucket         = data.aws_s3_object.package.bucket
  s3_key            = data.aws_s3_object.package.key
  s3_object_version = data.aws_s3_object.package.version_id

  environment = {
    variables = var.env_vars
  }
}

data "aws_s3_object" "package" {
  bucket = var.s3_bucket
  key    = var.s3_key
}

