module "service" {
  source = "../../../infrastructure/modules/worker"

  name = local.service_name

  deployment_service_env  = var.deployment_service_env
  deployment_service_name = var.deployment_service_name

  image = var.container_image

  env_vars = {
    demultiplexer_queue_url = module.input_queue.url
    metrics_namespace       = local.service_name

    dynamo_table_name = aws_dynamodb_table.links.name

    topic_arn = module.output_topic.arn

    resource_type = var.resource_type
  }

  min_capacity = 0
  max_capacity = 3

  use_fargate_spot = true

  namespace_id = var.namespace_id

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  subnets = var.subnets

  security_group_ids = [
    var.interservice_security_group_id,
    var.service_egress_security_group_id,
  ]
  shared_logging_secrets = var.shared_logging_secrets
}
