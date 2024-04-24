module "ftp_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name    = "ebsco-adapter-ftp"
  runtime = "python3.10"

  filename    = data.archive_file.empty_zip.output_path
  handler     = "main.lambda_handler"
  memory_size = 512
  timeout     = 10 * 60 // 10 minutes

  environment = {
    variables = {
      S3_BUCKET = aws_s3_bucket.ebsco_adapter.bucket
      S3_PREFIX = "prod"

      FTP_SERVER       = aws_ssm_parameter.ebsco_adapter_ftp_server.value
      FTP_USERNAME     = aws_ssm_parameter.ebsco_adapter_ftp_username.value
      FTP_PASSWORD     = aws_ssm_parameter.ebsco_adapter_ftp_password.value
      FTP_REMOTE_DIR   = aws_ssm_parameter.ebsco_adapter_ftp_remote_dir.value
      CUSTOMER_ID      = aws_ssm_parameter.ebsco_adapter_customer_id.value
      OUTPUT_TOPIC_ARN = module.ebsco_adapter_output_topic.arn
    }
  }

  depends_on = [
    aws_s3_bucket.ebsco_adapter,
    aws_ssm_parameter.ebsco_adapter_ftp_server,
    aws_ssm_parameter.ebsco_adapter_ftp_username,
    aws_ssm_parameter.ebsco_adapter_ftp_password,
    aws_ssm_parameter.ebsco_adapter_ftp_remote_dir,
    aws_ssm_parameter.ebsco_adapter_customer_id
  ]
}

data "archive_file" "empty_zip" {
  output_path = "data/empty.zip"
  type        = "zip"
  source {
    content  = "// This file is intentionally left empty"
    filename = "lambda.py"
  }
}

data "aws_iam_policy_document" "rw_ebsco_adapter_bucket" {
  statement {
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:List*"
    ]

    resources = [
      "${aws_s3_bucket.ebsco_adapter.arn}/*"
    ]
  }

  statement {
    actions = [
      "s3:ListBucket"
    ]

    resources = [
      "${aws_s3_bucket.ebsco_adapter.arn}"
    ]
  }
}

resource "aws_iam_role_policy" "ftp_lambda_policy" {
  role   = module.ftp_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.rw_ebsco_adapter_bucket.json
}
