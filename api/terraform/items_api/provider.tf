provider "aws" {
  region = "eu-west-1"

  # Ignore deployment tags on services
  ignore_tags {
    keys = ["deployment:label"]
  }

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}
