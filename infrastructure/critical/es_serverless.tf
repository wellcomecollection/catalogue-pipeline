# Elastic Cloud Serverless reaches the delta VPC through its own endpoint
# service and private zone, distinct from the hosted PrivateLink in
# platform-infrastructure. One endpoint and zone serve every serverless project.
locals {
  es_serverless_vpce_service_name = "com.amazonaws.vpce.eu-west-1.vpce-svc-063ee4d57bcb7e850"
  es_serverless_private_zone      = "private.eu-west-1.aws.elastic.cloud"
}

resource "aws_security_group" "es_serverless_vpce" {
  name        = "allow_elastic_serverless_vpce"
  description = "Members reach the Elastic Cloud Serverless interface endpoint"
  vpc_id      = local.vpc_id_new

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  tags = {
    Name = "allow_elastic_serverless_vpce"
  }
}

resource "aws_vpc_endpoint" "es_serverless" {
  vpc_id              = local.vpc_id_new
  service_name        = local.es_serverless_vpce_service_name
  vpc_endpoint_type   = "Interface"
  subnet_ids          = local.private_subnets_new
  security_group_ids  = [aws_security_group.es_serverless_vpce.id]
  private_dns_enabled = false

  tags = {
    Name = "elastic-cloud-serverless"
  }
}

resource "aws_route53_zone" "es_serverless_private" {
  name = local.es_serverless_private_zone

  vpc {
    vpc_id = local.vpc_id_new
  }
}

resource "aws_route53_record" "es_serverless_wildcard" {
  zone_id = aws_route53_zone.es_serverless_private.zone_id
  name    = "*.${local.es_serverless_private_zone}"
  type    = "CNAME"
  ttl     = 60
  records = [aws_vpc_endpoint.es_serverless.dns_entry[0]["dns_name"]]
}

# The FOLIO pipeline environment (wellcomecollection/platform#6534). No traffic
# policy is attached: like the hosted cluster's public_internet filter, the
# endpoint stays public behind API keys, and services use the private host.
# Attaching any policy rejects every source it doesn't list, Terraform included.
module "es_serverless_2026_10_20" {
  source = "./modules/es-serverless-project"

  name = "pipeline-storage-2026-10-20"

  # The API floor. The environment is idle between reindexes, and search power
  # sizes only the search tier, not writes.
  search_power = 28

  tags = {
    purpose = "folio-pipeline-environment"
  }
}
