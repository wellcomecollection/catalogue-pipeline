module "sierra-adapter-20200604" {
  source = "./stack"

  namespace     = "sierra-adapter-20200604"
  release_label = "latest"

  dlq_alarm_arn          = local.dlq_alarm_arn
  infra_bucket           = var.infra_bucket
  lambda_error_alarm_arn = local.lambda_error_alarm_arn

  egress_security_group_id       = aws_security_group.egress_security_group.id
  interservice_security_group_id = aws_security_group.interservice_security_group.id

  private_subnets = local.private_subnets
  vpc_id          = local.vpc_id

  bibs_windows_topic_arns = [
    module.bibs_window_generator.topic_arn,
    module.bibs_reharvest_topic.arn,
  ]

  items_windows_topic_arns = [
    module.items_window_generator.topic_arn,
    module.items_reharvest_topic.arn,
  ]

  holdings_windows_topic_arns = [
    module.holdings_window_generator.topic_arn,
    module.holdings_reharvest_topic.arn,
  ]

  orders_windows_topic_arns = [
    module.orders_window_generator.topic_arn,
    module.orders_reharvest_topic.arn,
  ]

  reporting_reindex_topic_arn = local.reporting_reindex_topic_arn

  sierra_linker_image  = aws_ecr_repository.sierra_adapter_services["sierra_linker"].repository_url
  sierra_merger_image  = aws_ecr_repository.sierra_adapter_services["sierra_merger"].repository_url
  sierra_indexer_image = aws_ecr_repository.sierra_adapter_services["sierra_indexer"].repository_url

  deployment_env         = "prod"
  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  elastic_cloud_vpce_sg_id = data.terraform_remote_state.shared_infra.outputs["ec_platform_privatelink_sg_id"]
}
