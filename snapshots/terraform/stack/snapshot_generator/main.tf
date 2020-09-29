module "snapshot_generator_queue" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name = "snapshot_generator-${var.deployment_service_env}"
  topic_arns = [var.snapshot_generator_input_topic_arn]

  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

module "snapshot_generator" {
  source          = "../../modules/service"
  service_name    = "snapshot_generator-${var.deployment_service_env}"
  container_image = "${var.snapshot_generator_image}:env.${var.deployment_service_env}"

  security_group_ids = [
    aws_security_group.service_egress.id
  ]

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  env_vars = {
    queue_url        = module.snapshot_generator_queue.url
    topic_arn        = module.snapshot_generator_output_topic.arn
    metric_namespace = "snapshot_generator-${var.deployment_service_env}"
  }

  cpu    = 1024
  memory = 4096

  secret_env_vars = {
    es_host     = "catalogue/api/es_host"
    es_port     = "catalogue/api/es_port"
    es_protocol = "catalogue/api/es_protocol"
    es_username = "catalogue/api/es_username"
    es_password = "catalogue/api/es_password"
  }

  subnets           = var.subnets
  max_capacity      = 1
  queue_read_policy = module.snapshot_generator_queue.read_policy

  deployment_service_env  = var.deployment_service_env
  deployment_service_name = "snapshot-generator"
}

module "snapshot_generator_output_topic" {
  source = "../../modules/topic"

  name       = "snapshot_complete-${var.deployment_service_env}"
  role_names = [module.snapshot_generator.task_role_name]
}

module "merger_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.snapshot_generator_queue.name

  queue_high_actions = [module.snapshot_generator.scale_up_arn]
  queue_low_actions  = [module.snapshot_generator.scale_down_arn]
}
