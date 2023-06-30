provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-admin"
  }
}

provider "aws" {
  region = "eu-west-1"

  alias = "catalogue"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

provider "ec" {}

terraform {
  backend "s3" {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue/pipeline.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}
