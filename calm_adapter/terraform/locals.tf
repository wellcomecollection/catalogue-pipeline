locals {
  namespace = "calm-adapter"

  infra_bucket    = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  account_id      = data.aws_caller_identity.current.account_id
  aws_region      = "eu-west-1"
  dlq_alarm_arn   = data.terraform_remote_state.shared_infra.outputs.dlq_alarm_arn
  vpc_id          = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_delta_id
  private_subnets = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_delta_private_subnets

  calm_adapter_image = "${aws_ecr_repository.ecr_repository_calm_adapter.repository_url}:env.prod"

  env_vars = {
    calm_api_url          = "https://wt-calm.wellcome.ac.uk/CalmAPI/ContentService.asmx"
    calm_sqs_url          = module.calm_windows_queue.url
    calm_sns_topic        = module.calm_adapter_topic.arn
    vhs_dynamo_table_name = module.vhs.table_name
    vhs_bucket_name       = module.vhs.bucket_name
  }
  secret_env_vars = {
    calm_api_username = "calm_adapter/calm_api/username"
    calm_api_password = "calm_adapter/calm_api/password"
    suppressed_fields = "calm_adapter/suppressed_fields"
  }

  window_generator_interval = "60 minutes"
}
