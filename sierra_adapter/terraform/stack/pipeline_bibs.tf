module "bibs_reader_new" {
  source = "../sierra_reader_new"

  resource_type      = "bibs"
  windows_topic_arns = var.bibs_windows_topic_arns
  sierra_fields      = local.sierra_bibs_fields

  reader_bucket          = aws_s3_bucket.sierra_adapter.id
  namespace              = local.namespace_hyphen
  lambda_error_alarm_arn = var.lambda_error_alarm_arn
}

module "bibs_reader" {
  source = "./../sierra_reader"

  resource_type = "bibs"

  bucket_name        = aws_s3_bucket.sierra_adapter.id
  windows_topic_arns = var.bibs_windows_topic_arns

  sierra_fields = local.sierra_bibs_fields

  sierra_api_url = local.sierra_api_url

  container_image = local.sierra_reader_image

  lambda_error_alarm_arn = var.lambda_error_alarm_arn

  infra_bucket = var.infra_bucket

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen

  interservice_security_group_id = var.interservice_security_group_id

  fargate_service_boilerplate = local.fargate_service_boilerplate
}

module "bibs_merger" {
  source = "./../sierra_merger"

  resource_type = "bibs"

  container_image   = local.sierra_merger_image
  updates_topic_arn = module.bibs_reader_new.topic_arn

  vhs_table_name        = module.vhs_sierra.table_name
  vhs_bucket_name       = module.vhs_sierra.bucket_name
  vhs_read_write_policy = module.vhs_sierra.full_access_policy

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = local.namespace_hyphen

  interservice_security_group_id = var.interservice_security_group_id

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
