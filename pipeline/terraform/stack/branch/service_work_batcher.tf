
locals {
  wait_minutes = var.is_reindexing ? 45 : 5
}

data "aws_ecs_cluster" "cluster" {
  cluster_name = var.cluster_name
}

module "batcher_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name                 = "${var.namespace}_batcher-${local.tei_suffix}"
  topic_arns                 = [module.router_path_output_topic.arn]
  visibility_timeout_seconds = (local.wait_minutes + 5) * 60
  alarm_topic_arn            = var.dlq_alarm_arn
}

module "batcher" {
  source = "../../modules/service"

  namespace = var.namespace
  name      = "batcher-${local.tei_suffix}"

  container_image = var.batcher_image

  security_group_ids = [
    var.service_egress_security_group_id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = var.cluster_name
  cluster_arn  = data.aws_ecs_cluster.cluster.id

  env_vars = {
    metrics_namespace = "${local.namespace}_batcher"

    queue_url        = module.batcher_queue.url
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
  max_capacity = min(1, var.max_capacity)

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment

  queue_read_policy = module.batcher_queue.read_policy

  cpu    = 1024
  memory = 2048

  deployment_service_env  = var.release_label
  deployment_service_name = "work-batcher"
}

module "batcher_output_topic" {
  source = "../../modules/topic"

  name       = "${var.namespace}_batcher_output-${local.tei_suffix}"
  role_names = [module.batcher.task_role_name]
}

module "batcher_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.batcher_queue.name

  queue_high_actions = [module.batcher.scale_up_arn]
  queue_low_actions  = [module.batcher.scale_down_arn]
}
