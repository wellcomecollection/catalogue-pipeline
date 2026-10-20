terraform {
  backend "s3" {
    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue/infrastructure/serverless_spike.tfstate"
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

# Phase 2 provider: the credentials only exist once module.project has applied.
provider "elasticstack" {
  alias = "serverless"

  elasticsearch {
    username  = module.project.es_cluster.username
    password  = module.project.es_cluster.password
    endpoints = [module.project.es_cluster.https_endpoint]
  }
}

# The hosted comparator lives on the shared 2026-07-03 cluster.
provider "elasticstack" {
  alias = "hosted"

  elasticsearch {
    username  = local.hosted_cluster.username
    password  = local.hosted_cluster.password
    endpoints = [local.hosted_cluster.https_endpoint]
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

data "terraform_remote_state" "catalogue_infra_critical" {
  backend = "s3"

  config = {
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/catalogue/infrastructure/critical.tfstate"
    region = "eu-west-1"

    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
  }
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
