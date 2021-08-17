locals {
  namespace     = "calm-adapter"
  release_label = "prod"

  infra_bucket           = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  account_id             = data.aws_caller_identity.current.account_id
  dlq_alarm_arn          = data.terraform_remote_state.monitoring.outputs.platform_dlq_alarm_topic_arn
  vpc_id                 = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets        = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]
  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  elastic_cloud_vpce_sg_id = data.terraform_remote_state.shared_infra.outputs.ec_platform_privatelink_sg_id

  reindex_jobs_topic_arn          = data.terraform_remote_state.reindexer.outputs.topic_arn
  calm_deletion_checker_topic_arn = data.terraform_remote_state.reindexer.outputs.calm_deletion_checker_topic_arn
  calm_reporting_topic_arn        = data.terraform_remote_state.reindexer.outputs.calm_reporting_topic_arn
  calm_api_url                    = "https://archives.wellcome.ac.uk/CalmAPI/ContentService.asmx"

  deletion_checking_enabled = true

  window_generator_interval = "60 minutes"
  deletion_check_interval   = "7 days"
}
