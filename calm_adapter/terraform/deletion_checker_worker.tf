module "calm_deletion_checker" {
  source = "../../pipeline/terraform/modules/fargate_service"

  name            = "calm_deletion_checker"
  container_image = local.calm_deletion_checker_image

  topic_arns = [local.calm_deletion_checker_topic_arn]

  queue_name                       = "calm-deletion-checker-input"
  queue_visibility_timeout_seconds = 30 * 60   # 30 minutes

  env_vars = {
    calm_api_url          = local.calm_api_url
    topic_arn             = module.calm_deletions_topic.arn
    vhs_dynamo_table_name = module.vhs.table_name
    // Choosing the batch size is a tradeoff between number of requests
    // and the size of those requests; smaller batches mean more requests
    // but with a smaller maximum request size.
    //
    // Given that the Calm API errors before resource exhaustion occurs
    // it seems that batch size might be an issue, so this has been tuned
    // down from 1000.
    batch_size = 512
  }

  secret_env_vars = {
    calm_api_username = "calm_adapter/calm_api/username"
    calm_api_password = "calm_adapter/calm_api/password"
  }

  min_capacity = 0

  // Here be dragons: don't scale this up or else you might
  // knock over the Calm server.
  max_capacity = local.deletion_checking_enabled ? 1 : 0

  cpu    = 512
  memory = 1024

  fargate_service_boilerplate = local.fargate_service_boilerplate

  security_group_ids = [
    aws_security_group.egress.id,
  ]
}
