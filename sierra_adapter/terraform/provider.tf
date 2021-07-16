provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-admin"
  }

  ignore_tags {
    keys = ["deployment:label"]
  }
}

data "aws_caller_identity" "current" {}
