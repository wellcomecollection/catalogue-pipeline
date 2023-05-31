module "items_reader_new" {
  source = "../sierra_reader_new"

  resource_type      = "items"
  windows_topic_arns = var.items_windows_topic_arns
  sierra_fields      = local.sierra_items_fields

  reader_bucket          = aws_s3_bucket.sierra_adapter.id
  namespace              = local.namespace_hyphen
  lambda_error_alarm_arn = var.lambda_error_alarm_arn
}

module "items_reader" {
  source = "./../sierra_reader"

  resource_type = "items"

  bucket_name        = aws_s3_bucket.sierra_adapter.id
  windows_topic_arns = []

  sierra_fields = local.sierra_items_fields

  sierra_api_url = local.sierra_api_url

  container_image = local.sierra_reader_image

  lambda_error_alarm_arn = var.lambda_error_alarm_arn

  infra_bucket = var.infra_bucket

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen

  interservice_security_group_id = var.interservice_security_group_id

  fargate_service_boilerplate = local.fargate_service_boilerplate
}

module "item_linker" {
  source = "./../sierra_linker"

  resource_type = "items"

  demultiplexer_topic_arn = module.items_reader_new.topic_arn

  container_image = local.sierra_linker_image

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen

  interservice_security_group_id = var.interservice_security_group_id

  fargate_service_boilerplate = local.fargate_service_boilerplate
}

module "items_merger" {
  source = "./../sierra_merger"

  resource_type     = "items"
  updates_topic_arn = module.item_linker.topic_arn

  container_image = local.sierra_merger_image

  vhs_table_name        = module.vhs_sierra.table_name
  vhs_bucket_name       = module.vhs_sierra.bucket_name
  vhs_read_write_policy = module.vhs_sierra.full_access_policy

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen

  interservice_security_group_id = var.interservice_security_group_id

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
