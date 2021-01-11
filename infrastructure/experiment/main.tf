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

provider "ec" {}

provider "aws" {
  region  = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

locals {
  ec_eu_west_1_service_name = "com.amazonaws.vpce.eu-west-1.vpce-svc-01f2afe87944eb12b"
  catalogue_outputs         = data.terraform_remote_state.accounts_catalogue.outputs

  public_subnets = local.catalogue_outputs["catalogue_vpc_public_subnets"]
  private_subnets  = local.catalogue_outputs["catalogue_vpc_private_subnets"]
  vpc_id          = local.catalogue_outputs["catalogue_vpc_id"]

  ifconfig_co_json = jsondecode(data.http.my_public_ip.body)

  ssh_key_name = "wellcomedigitalcatalogue"
}

resource "aws_security_group" "allow_ec_vpce" {
  provider = aws

  name = "allow_ec_vpce"
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

  subnet_ids = local.private_subnets

  private_dns_enabled = false
}

resource "ec_deployment" "example_minimal" {
  provider = ec

  name = "my_example_deployment"

  region                 = "eu-west-1"
  version                = "7.10.1"
  deployment_template_id = "aws-io-optimized-v2"

  traffic_filter = [
    ec_deployment_traffic_filter.allow_vpc_endpoint.id
  ]

  elasticsearch {}

  kibana {}
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

resource "aws_route53_zone" "vpce_ec" {
  name = "vpce.eu-west-1.aws.elastic-cloud.com"

  vpc {
    vpc_id = local.vpc_id
  }
}

resource "aws_route53_record" "cname_ec" {
  zone_id = aws_route53_zone.vpce_ec.zone_id
  name    = "*.vpce.eu-west-1.aws.elastic-cloud.com"
  type    = "CNAME"
  ttl     = "60"
  records = [aws_vpc_endpoint.ec2.dns_entry[0]["dns_name"]]
}

# All infra below here is to sat up an EC2 instance to test the connection
# After SSHing in to the dev instance, this can be tested by running:
# curl https://<deployment_id>.vpce.eu-west-1.aws.elastic-cloud.com:9243
# Where deployment_id is the id found for that deployment in the Elastic Cloud console.

resource "aws_instance" "dev_instance" {
  ami = data.aws_ami.amazon_linux.id
  instance_type = "t3.medium"

  iam_instance_profile = aws_iam_instance_profile.dev_instance_profile.name

  key_name = local.ssh_key_name

  tags = {
    Name = "ElasticCloudPrivateLinkTest"
    LastUpdated   = timestamp()
  }

  vpc_security_group_ids = [
    aws_security_group.allow_ec_vpce.id,
    aws_security_group.allow_ssh_dev_ip.id
  ]

  subnet_id = local.public_subnets[0]
}

resource "aws_iam_instance_profile" "dev_instance_profile" {
  name_prefix = "dev_instance_profile"
  role        = aws_iam_role.dev_instance_role.name
}

resource "aws_iam_role_policy" "role_assumer" {
  role   = aws_iam_role.dev_instance_role.name
  policy = data.aws_iam_policy_document.role_assumer.json
}

resource "aws_iam_role" "dev_instance_role" {
  name_prefix        = "dev_instance_role"
  assume_role_policy = data.aws_iam_policy_document.assume_role_policy.json

  tags = {
    Name        = "ElasticCloudPrivateLinkTest"
    LastUpdated = timestamp()
  }
}

resource "aws_security_group" "allow_ssh_dev_ip" {
  name_prefix = "allow_ssh_dev_ip"
  description = "Allow inbound SSH traffic to local public IP"
  vpc_id      = local.vpc_id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "TCP"
    cidr_blocks = ["${local.ifconfig_co_json.ip}/32"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "TCP"
    cidr_blocks = ["${local.ifconfig_co_json.ip}/32"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "ElasticCloudPrivateLinkTest"
    LastUpdated = timestamp()
  }
}

data "aws_ami" "amazon_linux" {
  most_recent = true

  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-x86_64-ebs"]
  }

  owners = ["amazon"]
}

data "aws_iam_policy_document" "role_assumer" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRole",
    ]

    resources = ["arn:aws:iam::756629837203:role/catalogue-read_only"]
  }
}

data "aws_iam_policy_document" "assume_role_policy" {
  statement {
    effect = "Allow"


    principals {
      type = "Service"
      identifiers = [
        "ec2.amazonaws.com"
      ]
    }

    actions = [
      "sts:AssumeRole",
    ]
  }
}

data "http" "my_public_ip" {
  url = "https://ifconfig.co/json"
  request_headers = {
    Accept = "application/json"
  }
}
