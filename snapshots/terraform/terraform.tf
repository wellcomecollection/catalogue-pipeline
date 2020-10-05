terraform {
  backend "s3" {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue/snapshots.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}

data "terraform_remote_state" "catalogue_account" {
  backend = "s3"

  config = {
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/platform-infrastructure/accounts/catalogue.tfstate"

    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    region   = "eu-west-1"
  }
}

data "terraform_remote_state" "shared" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"

    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/platform-infrastructure/shared.tfstate"
    region = "eu-west-1"
  }
}

data "terraform_remote_state" "data_api" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-read_only"

    bucket = "wellcomecollection-catalogue-infra-delta"
    key    = "terraform/catalogue/api/data_api.tfstate"
    region = "eu-west-1"
  }
}

data "terraform_remote_state" "api_shared" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-read_only"

    bucket = "wellcomecollection-catalogue-infra-delta"
    key    = "terraform/catalogue/api/shared.tfstate"
    region = "eu-west-1"
  }
}
