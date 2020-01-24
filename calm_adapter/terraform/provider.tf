provider "aws" {
  region  = "${local.aws_region}"
  version = "1.60.0"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }
}
