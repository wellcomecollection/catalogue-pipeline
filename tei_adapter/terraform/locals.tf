locals {
  infra_bucket           = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  aws_region             = "eu-west-1"
  lambda_error_alarm_arn = data.terraform_remote_state.shared_infra.outputs.lambda_error_alarm_arn
  namespace     = "tei-adapter"
  release_label = "prod"
  dlq_alarm_arn          = data.terraform_remote_state.shared_infra.outputs.dlq_alarm_arn
  vpc_id                 = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets        = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]
  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging
  elastic_cloud_vpce_sg_id = data.terraform_remote_state.shared_infra.outputs.ec_platform_privatelink_sg_id
}
