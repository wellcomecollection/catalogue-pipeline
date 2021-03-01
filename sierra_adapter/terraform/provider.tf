provider "aws" {
  region = var.aws_region

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }

  ignore_tags {
    keys = ["deployment:label"]
  }
}

data "aws_caller_identity" "current" {}
