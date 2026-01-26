locals {
  namespace     = "calm-adapter"
  release_label = "prod"

  infra_bucket           = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  dlq_alarm_arn          = data.terraform_remote_state.monitoring.outputs.platform_dlq_alarm_topic_arn
  vpc_id                 = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets        = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]
  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  elastic_cloud_vpce_sg_id = data.terraform_remote_state.shared_infra.outputs.ec_platform_privatelink_sg_id

  reindex_jobs_topic_arn          = data.terraform_remote_state.reindexer.outputs.topic_arn
  calm_deletion_checker_topic_arn = data.terraform_remote_state.reindexer.outputs.calm_deletion_checker_topic_arn
  calm_reporting_topic_arn        = data.terraform_remote_state.reindexer.outputs.calm_reporting_topic_arn

  calm_api_url = "https://archives.wellcome.org/CalmAPI/ContentService.asmx"

  deletion_checking_enabled = true

  window_generator_interval = "60 minutes"
  deletion_check_interval   = "7 days"

  fargate_service_boilerplate = {
    elastic_cloud_vpce_security_group_id = local.elastic_cloud_vpce_sg_id

    cluster_name = aws_ecs_cluster.cluster.name
    cluster_arn  = aws_ecs_cluster.cluster.arn

    dlq_alarm_topic_arn = local.dlq_alarm_arn

    egress_security_group_id = aws_security_group.egress.id

    namespace = local.namespace
    subnets   = local.private_subnets

    shared_logging_secrets = local.shared_logging_secrets
  }
}
