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

  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    ec = {
      source  = "elastic/ec"
      version = "0.13.0"
    }
    elasticstack = {
      source  = "elastic/elasticstack"
      version = "0.16.1"
    }
    random = {
      source  = "hashicorp/random"
      version = ">= 3.5.0"
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

data "terraform_remote_state" "shared_infra" {
  backend = "s3"

  config = {
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/platform-infrastructure/shared.tfstate"
    region = "eu-west-1"

    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
  }
}

provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-admin"
  }
}

provider "aws" {
  region = "eu-west-1"
  alias  = "catalogue"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

provider "ec" {}
