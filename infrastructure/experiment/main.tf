terraform {
  required_version = ">= 0.12"

  required_providers {
    ec = {
      source  = "elastic/ec"
      version = "0.1.0-beta"
    }
  }
}

data "terraform_remote_state" "accounts_catalogue" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"

    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/platform-infrastructure/accounts/catalogue.tfstate"
    region = "eu-west-1"
  }
}

provider "ec" {
  apikey = "<api_key>"
}

provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

locals {
  ec_eu_west_1_service_name = "com.amazonaws.vpce.eu-west-1.vpce-svc-01f2afe87944eb12b"
  catalogue_vpcs            = data.terraform_remote_state.accounts_catalogue.outputs
  vpc_id                    = local.catalogue_vpcs["catalogue_vpc_delta_id"]
}

resource "aws_security_group" "allow_ec_vpce" {
  provider = aws

  name   = "allow_ec_vpce"
  vpc_id = local.vpc_id

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

}

resource "aws_vpc_endpoint" "ec2" {
  provider = aws

  vpc_id            = local.vpc_id
  service_name      = local.ec_eu_west_1_service_name
  vpc_endpoint_type = "Interface"

  security_group_ids = [
    aws_security_group.allow_ec_vpce.id,
  ]

  private_dns_enabled = true
}

resource "ec_deployment" "example_minimal" {
  provider = ec

  name = "my_example_deployment"

  region                 = "eu-west-1"
  version                = "7.10.1"
  deployment_template_id = "aws-io-optimized-v2"

  elasticsearch {}

  kibana {}
}

resource "ec_deployment_traffic_filter_association" "allow_vpc_endpoint" {
  provider = ec

  traffic_filter_id = ec_deployment_traffic_filter.allow_vpc_endpoint.id
  deployment_id     = ec_deployment.example_minimal.id
}

resource "ec_deployment_traffic_filter" "allow_vpc_endpoint" {
  provider = ec

  name   = "ec_allow_vpc_endpoint"
  region = "eu-west-1"
  type   = "vpce"

  rule {
    source = aws_vpc_endpoint.ec2.id
  }
}
