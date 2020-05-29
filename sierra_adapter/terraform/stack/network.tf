resource "aws_service_discovery_private_dns_namespace" "namespace" {
  name = local.namespace_hyphen
  vpc  = var.vpc_id
}
