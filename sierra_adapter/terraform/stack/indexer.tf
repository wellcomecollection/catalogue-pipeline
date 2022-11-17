module "sierra_indexer" {
  source = "./../sierra_indexer"

  container_image = local.sierra_indexer_image

  sierra_adapter_bucket = module.vhs_sierra.bucket_name

  topic_arns = [
    module.bibs_merger.topic_arn,
    module.items_merger.topic_arn,
    module.holdings_merger.topic_arn,
    module.orders_merger.topic_arn,
    var.reporting_reindex_topic_arn,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  dlq_alarm_arn          = var.dlq_alarm_arn
  lambda_error_alarm_arn = var.lambda_error_alarm_arn

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen
  subnets      = var.private_subnets

  service_egress_security_group_id = var.egress_security_group_id
  interservice_security_group_id   = var.interservice_security_group_id
  elastic_cloud_vpce_sg_id         = var.elastic_cloud_vpce_sg_id

  shared_logging_secrets  = var.shared_logging_secrets
}