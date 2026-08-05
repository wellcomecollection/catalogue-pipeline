locals {
  infra_bucket                     = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  namespace                        = "tei-adapter"
  release_label                    = "prod"
  vpc_id                           = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets                  = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]
  shared_logging_secrets           = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging
  elastic_cloud_vpce_sg_id         = data.terraform_remote_state.shared_infra.outputs.ec_platform_privatelink_sg_id
  admin_cidr_ingress               = data.aws_ssm_parameter.admin_cidr_ingress.value
  min_capacity                     = 0
  max_capacity                     = 15
  rds_max_connections              = 45
  tei_id_extractor_max_connections = 5
  rds_lock_timeout_seconds         = 10 * 60

  # tei_id_extractor and tei_adapter both delay processing of *Deleted*
  # messages, to avoid mistaking a file rename (delete-of-old + add-of-new)
  # for a genuine deletion before the corresponding "changed" message has
  # landed. Each queue's visibility timeout MUST exceed its own delete
  # delay by a comfortable margin - otherwise SQS redelivers an in-flight
  # delete message before the delay finishes, causing it to loop
  # indefinitely and land in the DLQ without ever completing processing.
  tei_adapter_delete_delay_seconds      = 2 * 60  # 2 minutes
  tei_id_extractor_delete_delay_seconds = 30 * 60 # 30 minutes
  visibility_timeout_buffer_seconds     = 10 * 60 # headroom beyond the delay for actual message processing

  monitoring_outputs = data.terraform_remote_state.monitoring.outputs

  lambda_error_alarm_arn = local.monitoring_outputs["platform_lambda_error_alerts_topic_arn"]
  dlq_alarm_arn          = local.monitoring_outputs["platform_dlq_alarm_topic_arn"]
}

data "aws_ssm_parameter" "admin_cidr_ingress" {
  name = "/infra_critical/config/prod/admin_cidr_ingress"
}
