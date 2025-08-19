data "archive_file" "empty_zip" {
  output_path = "data/empty.zip"
  type        = "zip"
  source {
    content  = "// This file is intentionally left empty"
    filename = "lambda.py"
  }
}

data "aws_caller_identity" "current" {}

data "terraform_remote_state" "ebsco_adapter" {
  backend = "s3"
  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }

    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/ebsco_adapter.tfstate"
    region = "eu-west-1"
  }
}