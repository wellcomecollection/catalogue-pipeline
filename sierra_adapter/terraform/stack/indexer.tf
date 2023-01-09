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

  lambda_error_alarm_arn = var.lambda_error_alarm_arn

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen

  interservice_security_group_id   = var.interservice_security_group_id

  fargate_service_boilerplate = local.fargate_service_boilerplate
}