resource "aws_lambda_function" "scheduled_lambda" {

  function_name = var.name
  description   = var.description

  role = aws_iam_role.lambda_role.arn

  handler = "lambda.main"
  runtime = "python3.7"
  timeout = var.timeout

  s3_bucket         = data.aws_s3_bucket_object.package.bucket
  s3_key            = data.aws_s3_bucket_object.package.key
  s3_object_version = data.aws_s3_bucket_object.package.version_id

  environment {
    variables = var.env_vars
  }
}

data "aws_s3_bucket_object" "package" {
  bucket = var.s3_bucket
  key    = var.s3_key
}

resource "aws_iam_role" "lambda_role" {
  name               = "${var.name}_role"
  assume_role_policy = data.aws_iam_policy_document.assume_lambda_role.json
}

data "aws_iam_policy_document" "assume_lambda_role" {
  statement {
    actions = [
      "sts:AssumeRole",
    ]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}
