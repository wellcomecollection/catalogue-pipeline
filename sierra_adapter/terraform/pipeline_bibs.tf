module "bibs_reader" {
  source = "./sierra_reader"

  resource_type = "bibs"

  bucket_name        = "wellcomecollection-platform-adapters-sierra"
  windows_topic_arns = module.bibs_window_generator.topic_arn

  sierra_fields = local.sierra_bibs_fields

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

module "bibs_merger" {
  source = "./bib_merger"

  container_image = local.sierra_bib_merger_image

  merged_dynamo_table_name = local.vhs_table_name

  updates_topic_arn = module.bibs_reader.topic_arn

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn
  vpc_id       = local.vpc_id

  dlq_alarm_arn = local.dlq_alarm_arn

  vhs_full_access_policy = local.vhs_full_access_policy

  bucket_name = local.vhs_bucket_name

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  namespace    = var.namespace
  subnets      = local.private_subnets

  service_egress_security_group_id = aws_security_group.egress_security_group.id
  interservice_security_group_id   = aws_security_group.interservice_security_group.id
}
