resource "aws_security_group" "allow_catalogue_pipeline_elastic_cloud_vpce" {
  provider = aws

  name   = "allow_elastic_cloud_vpce"
  vpc_id = local.vpc_id_new

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

}

resource "aws_vpc_endpoint" "catalogue_pipeline_elastic_cloud_vpce" {
  provider = aws

  vpc_id            = local.vpc_id_new
  service_name      = local.ec_eu_west_1_service_name
  vpc_endpoint_type = "Interface"

  security_group_ids = [
    aws_security_group.allow_catalogue_pipeline_elastic_cloud_vpce.id,
  ]

  subnet_ids = local.private_subnets_new

  private_dns_enabled = false
}

resource "ec_deployment_traffic_filter" "allow_catalogue_pipeline_vpce" {
  provider = ec

  name   = "ec_allow_vpc_endpoint"
  region = "eu-west-1"
  type   = "vpce"

  rule {
    source = aws_vpc_endpoint.catalogue_pipeline_elastic_cloud_vpce.id
  }
}

# This enables public access to the ES cluster (with the usual x-pack auth proviso)
# TODO: Restrict this to Wellcome internal IP addresses when physical office access is restored
resource "ec_deployment_traffic_filter" "public_internet" {
  name   = "public_access"
  region = "eu-west-1"
  type   = "ip"

  rule {
    source = "0.0.0.0/0"
  }
}

resource "aws_route53_zone" "catalogue_pipeline_elastic_cloud_vpce" {
  name = local.catalogue_pipeline_ec_vpce_domain

  vpc {
    vpc_id = local.vpc_id_new
  }
}

resource "aws_route53_record" "cname_ec" {
  zone_id = aws_route53_zone.catalogue_pipeline_elastic_cloud_vpce.zone_id
  name    = "*.vpce.eu-west-1.aws.elastic-cloud.com"
  type    = "CNAME"
  ttl     = "60"
  records = [aws_vpc_endpoint.catalogue_pipeline_elastic_cloud_vpce.dns_entry[0]["dns_name"]]
}
