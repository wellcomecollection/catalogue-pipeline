module "ftp_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name    = "ebsco-adapter-ftp"
  runtime = "python3.10"

  filename    = data.archive_file.empty_zip.output_path
  memory_size = 512
  timeout     = 10 * 60 // 10 minutes

  environment = {
    variables = {
      FOO = "bar"
    }
  }
}

data "archive_file" "empty_zip" {
  output_path = "data/empty.zip"
  type        = "zip"
  source {
    content  = "// This file is intentionally left empty"
    filename = "lambda.py"
  }
}
