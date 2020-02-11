provider "aws" {
  region = "eu-west-1"
  version = "~> 2.0"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }
}

terraform {
  backend "s3" {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue_pipeline_tf_0.12.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}

data "terraform_remote_state" "catalogue_infra_critical" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    bucket   = "wellcomecollection-platform-infra"
    key      = "terraform/catalogue/infrastructure/critical.tfstate"
    region   = "eu-west-1"
  }
}

data "terraform_remote_state" "catalogue_pipeline_data" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    bucket   = "wellcomecollection-platform-infra"
    key      = "terraform/catalogue_pipeline_data.tfstate"
    region   = "eu-west-1"
  }
}

data "terraform_remote_state" "shared_infra" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    bucket   = "wellcomecollection-platform-infra"
    key      = "terraform/shared_infra.tfstate"
    region   = "eu-west-1"
  }
}

data "terraform_remote_state" "sierra_adapter" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    bucket   = "wellcomecollection-platform-infra"
    key      = "terraform/sierra_adapter.tfstate"
    region   = "eu-west-1"
  }
}

data "terraform_remote_state" "mets_adapter" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    bucket   = "wellcomecollection-platform-infra"
    key      = "terraform/mets_adapter.tfstate"
    region   = "eu-west-1"
  }
}

data "terraform_remote_state" "reindexer" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    bucket   = "wellcomecollection-platform-infra"
    key      = "terraform/reindexer.tfstate"
    region   = "eu-west-1"
  }
}

data "aws_caller_identity" "current" {
}

