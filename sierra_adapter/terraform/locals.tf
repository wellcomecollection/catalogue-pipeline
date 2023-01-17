locals {
  monitoring_outputs = data.terraform_remote_state.monitoring.outputs

  lambda_error_alarm_arn = local.monitoring_outputs["platform_lambda_error_alerts_topic_arn"]
  dlq_alarm_arn          = local.monitoring_outputs["platform_dlq_alarm_topic_arn"]

  vpc_id          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]

  reporting_reindex_topic_arn = data.terraform_remote_state.shared_infra.outputs.reporting_sierra_reindex_topic_arn
}
