terraform {
  required_version = ">= 0.9"

  backend "s3" {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-developer"
    }

    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/ebsco_adapter.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}

data "terraform_remote_state" "monitoring" {
  backend = "s3"

  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }

    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/monitoring.tfstate"
    region = "eu-west-1"
  }
}

data "terraform_remote_state" "reindexer" {
  backend = "s3"

  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }

    bucket   = "wellcomecollection-platform-infra"
    key      = "terraform/catalogue/reindexer.tfstate"
    region   = "eu-west-1"
  }
}
