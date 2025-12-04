resource "aws_iam_role_policy" "read_adapter_store" {
  role   = module.transformer.task_role_name
  policy = var.adapter_config.read_policy
}

module "output_topic" {
  source = "../topic"

  name       = "${local.namespace}_transformer_${var.source_name}_output"
  role_names = [module.transformer.task_role_name]
}

module "transformer" {
  source = "../fargate_service"

  name            = "transformer_${var.source_name}"
  container_image = var.container_image

  topic_arns = local.topic_arns

  queue_visibility_timeout_seconds = var.queue_visibility_timeout_seconds

  env_vars = merge(
    {
      sns_topic_arn = module.output_topic.arn,
    },
    var.env_vars
  )

  secret_env_vars = var.secret_env_vars

  cpu    = var.cpu
  memory = var.memory

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  fargate_service_boilerplate = var.fargate_service_boilerplate

  trigger_values = var.trigger_values
}

