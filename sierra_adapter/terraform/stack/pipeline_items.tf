module "items_reader" {
  source = "./../sierra_reader"

  resource_type = "items"

  bucket_name        = aws_s3_bucket.sierra_adapter.id
  windows_topic_arns = var.items_windows_topic_arns

  sierra_fields = local.sierra_items_fields

  sierra_api_url = local.sierra_api_url

  container_image = local.sierra_reader_image

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn
  vpc_id       = var.vpc_id

  dlq_alarm_arn          = var.dlq_alarm_arn
  lambda_error_alarm_arn = var.lambda_error_alarm_arn

  infra_bucket = var.infra_bucket

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen
  subnets      = var.private_subnets

  service_egress_security_group_id = var.egress_security_group_id
  interservice_security_group_id   = var.interservice_security_group_id
}

module "items_to_dynamo" {
  source = "./../items_to_dynamo"

  demultiplexer_topic_arn = module.items_reader.topic_arn

  container_image = local.sierra_items_to_dynamo_image

  vhs_sierra_items_full_access_policy = module.vhs_sierra_items.full_access_policy
  vhs_sierra_items_table_name         = module.vhs_sierra_items.table_name
  vhs_sierra_items_bucket_name        = module.vhs_sierra_items.bucket_name

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn
  vpc_id       = var.vpc_id

  dlq_alarm_arn = var.dlq_alarm_arn

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen
  subnets      = var.private_subnets

  service_egress_security_group_id = var.egress_security_group_id
  interservice_security_group_id   = var.interservice_security_group_id
}

module "items_merger" {
  source = "./../item_merger"

  container_image = local.sierra_item_merger_image

  updates_topic_arn         = module.items_to_dynamo.topic_arn

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn
  vpc_id       = var.vpc_id

  dlq_alarm_arn = var.dlq_alarm_arn

  sierra_transformable_vhs_full_access_policy = module.vhs_sierra.full_access_policy
  sierra_transformable_vhs_dynamo_table_name = module.vhs_sierra.table_name
  sierra_transformable_vhs_bucket_name = module.vhs_sierra.bucket_name

  sierra_items_vhs_bucket_name = module.vhs_sierra_items.bucket_name
  sierra_items_vhs_dynamo_table_name = module.vhs_sierra_items.table_name
  sierra_items_vhs_read_policy = module.vhs_sierra_items.read_policy

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen
  subnets      = var.private_subnets

  service_egress_security_group_id = var.egress_security_group_id
  interservice_security_group_id   = var.interservice_security_group_id
}
