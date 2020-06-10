module "items_reader" {
  source = "./sierra_reader"

  resource_type = "items"

  bucket_name        = aws_s3_bucket.sierra_adapter.id
  windows_topic_arns = module.items_window_generator.topic_arn

  sierra_fields = local.sierra_items_fields

  sierra_api_url = local.sierra_api_url

  container_image = local.sierra_reader_image

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn
  vpc_id       = local.vpc_id

  dlq_alarm_arn          = local.dlq_alarm_arn
  lambda_error_alarm_arn = local.lambda_error_alarm_arn

  infra_bucket = var.infra_bucket

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = var.namespace
  subnets      = local.private_subnets

  service_egress_security_group_id = aws_security_group.egress_security_group.id
  interservice_security_group_id   = aws_security_group.interservice_security_group.id
}

module "items_to_dynamo" {
  source = "./items_to_dynamo"

  demultiplexer_topic_arn = module.items_reader.topic_arn

  container_image = local.sierra_items_to_dynamo_image

  vhs_sierra_items_full_access_policy = local.vhs_sierra_items_full_access_policy
  vhs_sierra_items_table_name         = local.vhs_sierra_items_table_name
  vhs_sierra_items_bucket_name        = local.vhs_sierra_items_bucket_name

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn
  vpc_id       = local.vpc_id

  dlq_alarm_arn = local.dlq_alarm_arn

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = var.namespace
  subnets      = local.private_subnets

  service_egress_security_group_id = aws_security_group.egress_security_group.id
  interservice_security_group_id   = aws_security_group.interservice_security_group.id
}

data "aws_sns_topic" "reindexed_items" {
  name = local.reindexed_items_topic_name
}

module "items_merger" {
  source = "./item_merger"

  container_image = local.sierra_item_merger_image

  merged_dynamo_table_name = local.vhs_table_name

  updates_topic_arn = module.items_to_dynamo.topic_arn

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn
  vpc_id       = local.vpc_id

  dlq_alarm_arn = local.dlq_alarm_arn

  vhs_full_access_policy = local.vhs_full_access_policy

  bucket_name = local.vhs_bucket_name

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = var.namespace
  subnets      = local.private_subnets

  sierra_items_bucket = local.vhs_sierra_items_bucket_name

  service_egress_security_group_id = aws_security_group.egress_security_group.id
  interservice_security_group_id   = aws_security_group.interservice_security_group.id
}
