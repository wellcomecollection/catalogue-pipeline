data "archive_file" "empty_zip" {
  output_path = "data/empty.zip"
  type        = "zip"
  source {
    content  = "// This file is intentionally left empty"
    filename = "lambda.py"
  }
}

data "aws_caller_identity" "current" {}

data "terraform_remote_state" "accounts_catalogue" {
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

data "terraform_remote_state" "shared_infra" {
  backend = "s3"

  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/platform-infrastructure/shared.tfstate"
    region = "eu-west-1"
  }
}

locals {
  namespace = "ebsco_adapter"

  shared_infra   = data.terraform_remote_state.shared_infra.outputs
  catalogue_vpcs = data.terraform_remote_state.accounts_catalogue.outputs

  network_config = {
    subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]
    vpc_id  = local.catalogue_vpcs["catalogue_vpc_delta_id"]

    ec_privatelink_security_group_id = local.shared_infra["ec_platform_privatelink_sg_id"]
  }

  pipeline_date = "2025-08-14"
  index_date    = "2025-09-04"
}
