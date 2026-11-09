locals {
  default_tags = {
    TerraformConfigurationURL = "https://github.com/wellcomecollection/catalogue-pipeline/tree/main/reindexer/terraform"
    "github.com/project-slug" = "wellcomecollection/catalogue-pipeline"
  }
}

provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }

  default_tags {
    tags = local.default_tags
  }
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
