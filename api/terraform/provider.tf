provider "aws" {
  region  = "${var.aws_region}"
  version = "1.57.0"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}
