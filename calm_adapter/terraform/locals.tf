locals {
  namespace = "calm-adapter"

  infra_bucket    = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  account_id      = data.aws_caller_identity.current.account_id
  aws_region      = "eu-west-1"
  dlq_alarm_arn   = data.terraform_remote_state.shared_infra.outputs.dlq_alarm_arn
  vpc_id          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]

  calm_deletion_checker_topic_arn = data.terraform_remote_state.reindexer.outputs.calm_deletion_checker_topic_arn
  calm_api_url                    = "https://wt-calm.wellcome.ac.uk/CalmAPI/ContentService.asmx"

  window_generator_interval = "60 minutes"
}
