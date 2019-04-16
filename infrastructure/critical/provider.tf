provider "aws" {
  region  = "${var.aws_region}"
  version = "1.46.0"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/admin"
  }
}
