provider "aws" {
  region  = "${var.aws_region}"
  version = "1.23.0"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }
}

data "aws_caller_identity" "current" {}
