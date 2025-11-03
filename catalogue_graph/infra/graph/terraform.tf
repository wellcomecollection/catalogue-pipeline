terraform {
  required_version = ">= 0.11"

  backend "s3" {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-developer"
    }
    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue/graph.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}

data "terraform_remote_state" "platform_infra" {
  backend = "s3"

  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/aws-account-infrastructure/catalogue.tfstate"
    region = "eu-west-1"
  }
}

provider "aws" {
  region = "eu-west-1"
  alias  = "dns"

  assume_role {
    role_arn = "arn:aws:iam::267269328833:role/wellcomecollection-assume_role_hosted_zone_update"
  }
}

provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }
}
