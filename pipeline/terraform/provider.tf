# Although we've declared the provider at the top level, we need to redeclare it in
# the module. Otherwise, Terraform defaults to "hashicorp/{provider}"
# See https://github.com/hashicorp/terraform/issues/25172#issuecomment-641284286
terraform {
  required_version = ">= 0.13"
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }

    ec = {
      source  = "elastic/ec"
      version = "0.4.1"
    }

    elasticstack = {
      source  = "elastic/elasticstack"
      version = "0.3.3"
    }
  }
}

provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-admin"
  }

  ignore_tags {
    keys = ["deployment:label"]
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
