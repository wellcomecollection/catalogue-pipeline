
locals {
  wait_minutes = var.is_reindexing ? 45 : 5
}

data "aws_ecs_cluster" "cluster" {
  cluster_name = aws_ecs_cluster.cluster.name
}

module "batcher_input_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name                 = "${local.namespace}_batcher_input"
  topic_arns                 = [module.router_path_output_topic.arn]
  visibility_timeout_seconds = (local.wait_minutes + 5) * 60
  alarm_topic_arn            = var.dlq_alarm_arn
}

module "batcher" {
  source = "../modules/service"

  namespace = local.namespace
  name      = "batcher"

  container_image = local.batcher_image

  security_group_ids = [
    aws_security_group.egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = data.aws_ecs_cluster.cluster.id

  env_vars = {
    metrics_namespace = "${local.namespace}_batcher"

    queue_url        = module.batcher_input_queue.url
    output_topic_arn = module.batcher_output_topic.arn

    # NOTE: this needs to be less than visibility timeout
    flush_interval_minutes = local.wait_minutes

    # NOTE: SQS in flight limit is 120k
    max_processed_paths = var.is_reindexing ? 100000 : 5000

    max_batch_size = 40
  }

  secret_env_vars = {}

  shared_logging_secrets = var.shared_logging_secrets

  subnets = var.subnets

  min_capacity = var.min_capacity
  max_capacity = min(1, local.max_capacity)

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  queue_read_policy = module.batcher_input_queue.read_policy

  cpu    = 1024
  memory = 2048

  deployment_service_env  = var.release_label
  deployment_service_name = "work-batcher"
}

module "batcher_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_batcher_output"
  role_names = [module.batcher.task_role_name]
}

module "batcher_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.batcher_input_queue.name

  queue_high_actions = [module.batcher.scale_up_arn]
  queue_low_actions  = [module.batcher.scale_down_arn]
}
