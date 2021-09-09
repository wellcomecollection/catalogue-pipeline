module "sierra_transformer_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name      = "${local.namespace}_sierra_transformer"
  topic_arns      = local.sierra_adapter_topic_arns
  alarm_topic_arn = var.dlq_alarm_arn
}

module "sierra_transformer" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_sierra_transformer"
  container_image = local.transformer_sierra_image
  security_group_ids = [
    # TODO: Do we need the egress security group?
    aws_security_group.service_egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  env_vars = {
    transformer_queue_id = module.sierra_transformer_queue.url
    metrics_namespace    = "${local.namespace_hyphen}_sierra_transformer"

    sns_topic_arn = module.sierra_transformer_output_topic.arn

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

  queue_read_policy = module.sierra_transformer_queue.read_policy

  deployment_service_env  = var.release_label
  deployment_service_name = "sierra-transformer"
  shared_logging_secrets  = var.shared_logging_secrets
}

resource "aws_iam_role_policy" "sierra_transformer_vhs_sierra_adapter_read" {
  role   = module.sierra_transformer.task_role_name
  policy = var.vhs_sierra_read_policy
}

module "sierra_transformer_output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic?ref=v1.0.1"

  name = "${local.namespace}_sierra_transformer_output"
}

resource "aws_iam_role_policy" "allow_sierra_transformer_sns_publish" {
  role   = module.sierra_transformer.task_role_name
  policy = module.sierra_transformer_output_topic.publish_policy
}

module "sierra_transformer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.sierra_transformer_queue.name

  queue_high_actions = [module.sierra_transformer.scale_up_arn]
  queue_low_actions  = [module.sierra_transformer.scale_down_arn]
}
