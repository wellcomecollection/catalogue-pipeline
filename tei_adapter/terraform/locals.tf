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

  monitoring_outputs = data.terraform_remote_state.monitoring.outputs

  lambda_error_alarm_arn = local.monitoring_outputs["platform_lambda_error_alerts_topic_arn"]
  dlq_alarm_arn          = local.monitoring_outputs["platform_dlq_alarm_topic_arn"]
}

data "aws_ssm_parameter" "admin_cidr_ingress" {
  name = "/infra_critical/config/prod/admin_cidr_ingress"
}
