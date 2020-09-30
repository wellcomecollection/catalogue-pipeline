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

    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
    region   = "eu-west-1"
  }
}

data "terraform_remote_state" "shared" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/platform-infrastructure/shared.tfstate"
    region = "eu-west-1"
  }
}

