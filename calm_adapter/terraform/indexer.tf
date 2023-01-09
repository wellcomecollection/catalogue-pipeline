module "calm_indexer" {
  source = "../../pipeline/terraform/modules/fargate_service"

  name            = "calm_indexer"
  container_image = local.calm_indexer_image

  topic_arns = [
    module.calm_adapter_topic.arn,
    local.calm_reporting_topic_arn
  ]

  queue_name = "calm-indexer-input"

  min_capacity = 0
  max_capacity = 1

  env_vars = {
    es_index          = "calm_catalog"
    metrics_namespace = "calm_indexer"

    es_username = "calm_indexer"
    es_protocol = "https"
    es_port     = "9243"

    # TODO: Change the Calm adapter to look for the `queue_url` env var
    sqs_queue_url = module.calm_indexer.queue_url
  }

  omit_queue_url = true

  secret_env_vars = {
    es_password = "reporting/calm_indexer/es_password"
    es_host     = "reporting/es_host"
  }

  cpu    = 512
  memory = 1024

  fargate_service_boilerplate = local.fargate_service_boilerplate

  security_group_ids = [
    aws_security_group.egress.id,
  ]
}
