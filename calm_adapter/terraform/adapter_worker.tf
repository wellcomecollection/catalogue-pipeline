module "calm_adapter" {
  source = "../../pipeline/terraform/modules/fargate_service"

  name            = "calm_adapter"
  container_image = local.calm_adapter_image

  topic_arns = [aws_sns_topic.calm_windows_topic.arn]

  queue_name                       = "calm-windows"
  queue_visibility_timeout_seconds = 3 * 60 * 60 # 3 hours

  env_vars = {
    calm_api_url          = local.calm_api_url
    calm_sns_topic        = module.calm_adapter_topic.arn
    vhs_dynamo_table_name = module.vhs.table_name
    vhs_bucket_name       = module.vhs.bucket_name

    # TODO: Change the Calm adapter to look for the `queue_url` env var
    calm_sqs_url = module.calm_adapter.queue_url
  }

  omit_queue_url = true

  secret_env_vars = {
    calm_api_username = "calm_adapter/calm_api/username"
    calm_api_password = "calm_adapter/calm_api/password"
    suppressed_fields = "calm_adapter/suppressed_fields"
  }

  min_capacity = 0
  max_capacity = 2

  cpu    = 512
  memory = 1024

  fargate_service_boilerplate = local.fargate_service_boilerplate

  security_group_ids = [
    aws_security_group.egress.id,
  ]
}
