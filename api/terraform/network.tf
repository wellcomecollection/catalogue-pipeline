resource "aws_service_discovery_private_dns_namespace" "namespace" {
  name = local.namespace_hyphen
  vpc  = local.vpc_id
}

