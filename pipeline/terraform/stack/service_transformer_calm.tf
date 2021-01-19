module "calm_transformer_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_calm_transformer"
  topic_arns      = var.calm_adapter_topic_arns
  alarm_topic_arn = var.dlq_alarm_arn
  aws_region      = var.aws_region
}

module "calm_transformer" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_calm_transformer"
  container_image = local.transformer_calm_image
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  env_vars = {
    transformer_queue_id = module.calm_transformer_queue.url
    metrics_namespace    = "${local.namespace_hyphen}_calm_transformer"

    sns_topic_arn = module.calm_transformer_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["transformer"]

  subnets             = var.subnets
  max_capacity        = var.max_capacity
  messages_bucket_arn = aws_s3_bucket.messages.arn

  queue_read_policy = module.calm_transformer_queue.read_policy

  deployment_service_env  = var.release_label
  deployment_service_name = "calm-transformer"
  shared_logging_secrets  = var.shared_logging_secrets
}

resource "aws_iam_role_policy" "calm_transformer_vhs_calm_adapter_read" {
  role   = module.calm_transformer.task_role_name
  policy = var.vhs_calm_read_policy
}

module "calm_transformer_output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic?ref=v1.0.1"

  name = "${local.namespace_hyphen}_calm_transformer_output"
}

resource "aws_iam_role_policy" "allow_calm_transformer_sns_publish" {
  role   = module.calm_transformer.task_role_name
  policy = module.calm_transformer_output_topic.publish_policy
}

module "calm_transformer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.calm_transformer_queue.name

  queue_high_actions = [module.calm_transformer.scale_up_arn]
  queue_low_actions  = [module.calm_transformer.scale_down_arn]
}
