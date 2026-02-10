terraform {
  backend "s3" {
    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue/infrastructure/critical.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"

    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-developer"
    }
  }
}

data "terraform_remote_state" "accounts_catalogue" {
  backend = "s3"

  config = {
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/aws-account-infrastructure/catalogue.tfstate"
    region = "eu-west-1"

    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
  }
}

locals {
  catalogue_vpcs = data.terraform_remote_state.accounts_catalogue.outputs
}

provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-admin"
  }
}
