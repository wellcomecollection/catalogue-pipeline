module "transformer_calm_input_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name      = "${local.namespace}_transformer_calm_input"
  topic_arns      = local.calm_adapter_topic_arns
  alarm_topic_arn = var.dlq_alarm_arn
}

module "transformer_calm" {
  source = "../modules/service"

  namespace = local.namespace
  name      = "transformer_calm"

  container_image = local.transformer_calm_image
  security_group_ids = [
    aws_security_group.service_egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  env_vars = {
    transformer_queue_id = module.transformer_calm_input_queue.url
    metrics_namespace    = "${local.namespace_hyphen}_transformer_calm"

    sns_topic_arn = module.transformer_calm_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["transformer"]

  use_fargate_spot = true

  subnets = var.subnets

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  queue_read_policy = module.transformer_calm_input_queue.read_policy

  deployment_service_env  = var.release_label
  deployment_service_name = "calm-transformer"
  shared_logging_secrets  = var.shared_logging_secrets
}

resource "aws_iam_role_policy" "calm_transformer_vhs_calm_adapter_read" {
  role   = module.transformer_calm.task_role_name
  policy = var.vhs_calm_read_policy
}

module "transformer_calm_output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic?ref=v1.0.1"

  name = "${local.namespace}_transformer_calm_output"
}

resource "aws_iam_role_policy" "allow_calm_transformer_sns_publish" {
  role   = module.transformer_calm.task_role_name
  policy = module.transformer_calm_output_topic.publish_policy
}

module "calm_transformer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.transformer_calm_input_queue.name

  queue_high_actions = [module.transformer_calm.scale_up_arn]
  queue_low_actions  = [module.transformer_calm.scale_down_arn]
}
