# Serverless PrivateLink is a different endpoint service and DNS zone from the
# hosted one in platform-infrastructure, and that root pins ec 0.5.0, so the
# spike carries its own endpoint, zone and policy.
locals {
  serverless_vpce_service_name = "com.amazonaws.vpce.eu-west-1.vpce-svc-063ee4d57bcb7e850"
  serverless_private_zone      = "private.eu-west-1.aws.elastic.cloud"
}

resource "aws_security_group" "serverless_vpce" {
  name        = "allow_elastic_serverless_vpce_${local.pipeline_date}"
  description = "Members can reach the Elastic serverless interface endpoint"
  vpc_id      = local.vpc_id

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  tags = {
    Name = "allow_elastic_serverless_vpce_${local.pipeline_date}"
  }
}

resource "aws_vpc_endpoint" "serverless" {
  vpc_id            = local.vpc_id
  service_name      = local.serverless_vpce_service_name
  vpc_endpoint_type = "Interface"
  subnet_ids        = local.private_subnets

  security_group_ids = [aws_security_group.serverless_vpce.id]

  private_dns_enabled = false

  tags = {
    Name = "elastic-serverless-${local.pipeline_date}"
  }
}

resource "aws_route53_zone" "serverless_private" {
  name = local.serverless_private_zone

  vpc {
    vpc_id = local.vpc_id
  }
}

resource "aws_route53_record" "serverless_wildcard" {
  zone_id = aws_route53_zone.serverless_private.zone_id
  name    = "*.${local.serverless_private_zone}"
  type    = "CNAME"
  ttl     = 60
  records = [aws_vpc_endpoint.serverless.dns_entry[0]["dns_name"]]
}

resource "ec_serverless_traffic_filter" "private" {
  name        = "pipeline-storage-${local.pipeline_date}-private"
  region      = local.region_id
  type        = "vpce"
  description = "Delta VPC interface endpoint for the platform#6714 spike"

  rules = [
    {
      source      = aws_vpc_endpoint.serverless.id
      description = "catalogue delta VPC"
    }
  ]
}

resource "ec_serverless_traffic_filter" "ip" {
  count = length(var.allowed_ip_cidrs) > 0 ? 1 : 0

  name        = "pipeline-storage-${local.pipeline_date}-ip"
  region      = local.region_id
  type        = "ip"
  description = "Harness laptop for the platform#6714 spike"

  rules = [
    for cidr in var.allowed_ip_cidrs : {
      source = cidr
    }
  ]
}
