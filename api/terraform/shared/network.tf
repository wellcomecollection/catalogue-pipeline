resource "aws_service_discovery_private_dns_namespace" "namespace" {
  name = local.namespace_hyphen
  vpc  = local.vpc_id
}

module "interface_endpoints" {
  source = "../modules/interface_endpoints"

  vpc_id            = local.vpc_id
  security_group_id = aws_security_group.egress.id
  subnet_ids        = local.private_subnets
}
