provider "aws" {
  region = var.aws_region

  # Ignore deployment tags on services
  ignore_tags {
    keys = ["deployment:label"]
  }

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}
