locals {
  namespace                     = "calm-adapter"
  logstash_transit_service_name = "${local.namespace}_logstash_transit"
  logstash_host                 = "${local.logstash_transit_service_name}.${local.namespace}"

  infra_bucket    = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  account_id      = data.aws_caller_identity.current.account_id
  aws_region      = "eu-west-1"
  dlq_alarm_arn   = data.terraform_remote_state.shared_infra.outputs.dlq_alarm_arn
  vpc_id          = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_delta_id
  private_subnets = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_delta_private_subnets
}
