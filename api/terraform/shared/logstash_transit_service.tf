module "logstash_transit" {
  source = "./logstash_transit"

  name      = local.logstash_transit_service_name
  namespace = local.namespace

  cluster_arn = aws_ecs_cluster.catalogue_api.arn

  subnets = local.private_subnets

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id

  security_group_ids = [
    module.egress_security_group.sg_id,
    aws_security_group.interservice.id,
  ]

  aws_region = var.aws_region
}
