module "snapshot_generator" {
  source = "../../../../infrastructure/modules/worker"

  name  = "snapshot_generator-${var.deployment_service_env}"
  image = "${var.snapshot_generator_image}:env.${var.deployment_service_env}"

  env_vars = {
    queue_url        = module.snapshot_generator_input_queue.url
    topic_arn        = module.snapshot_generator_output_topic.arn
    metric_namespace = "snapshot_generator-${var.deployment_service_env}"
  }

  cpu    = 4096
  memory = 8192

  secret_env_vars = {
    es_host     = "catalogue/api/es_host"
    es_port     = "catalogue/api/es_port"
    es_protocol = "catalogue/api/es_protocol"
    es_username = "catalogue/api/es_username"
    es_password = "catalogue/api/es_password"
  }

  subnets = var.subnets

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  security_group_ids = [
    aws_security_group.egress.id
  ]

  min_capacity = 0
  max_capacity = 1

  deployment_service_env  = var.deployment_service_env
  deployment_service_name = "snapshot-generator"
}

module "snapshot_generator_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.snapshot_generator_input_queue.name

  queue_high_actions = [module.snapshot_generator.scale_up_arn]
  queue_low_actions  = [module.snapshot_generator.scale_down_arn]
}
