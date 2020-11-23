module "batcher_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name                 = "${local.namespace_hyphen}_batcher"
  topic_arns                 = [module.router_path_output_topic.arn]
  visibility_timeout_seconds = 3600
  aws_region                 = var.aws_region
  alarm_topic_arn            = var.dlq_alarm_arn
}

module "batcher" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_batcher"
  container_image = local.batcher_image

  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.id

  env_vars = {
    metrics_namespace = "${local.namespace_hyphen}_batcher"

    queue_url        = module.batcher_queue.url
    output_topic_arn = module.batcher_output_topic.arn

    flush_interval_minutes = 30
    max_processed_paths    = 50000
    max_batch_size         = 40
  }

  secret_env_vars = {}

  shared_logging_secrets = var.shared_logging_secrets

  subnets             = var.subnets
  max_capacity        = 1
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.batcher_queue.read_policy

  cpu    = 1024
  memory = 2048

  deployment_service_env  = var.release_label
  deployment_service_name = "work-batcher"
}

module "batcher_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_batcher_output"
  role_names = [module.batcher.task_role_name]

  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "batcher_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.batcher_queue.name

  queue_high_actions = [module.batcher.scale_up_arn]
  queue_low_actions  = [module.batcher.scale_down_arn]
}
