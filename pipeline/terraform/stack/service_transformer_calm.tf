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

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id

  env_vars = {
    sns_arn              = module.calm_transformer_topic.arn
    transformer_queue_id = module.calm_transformer_queue.url
    metrics_namespace    = "${local.namespace_hyphen}_calm_transformer"
    messages_bucket_name = aws_s3_bucket.messages.id
    vhs_calm_bucket_name = var.vhs_calm_sourcedata_bucket_name
    vhs_calm_table_name  = var.vhs_calm_sourcedata_table_name
  }

  secret_env_vars = {}

  subnets             = var.subnets
  aws_region          = var.aws_region
  max_capacity        = 10
  messages_bucket_arn = aws_s3_bucket.messages.arn

  queue_read_policy = module.calm_transformer_queue.read_policy
}

resource "aws_iam_role_policy" "calm_transformer_vhs_calm_adapter_read" {
  role   = module.calm_transformer.task_role_name
  policy = var.vhs_calm_read_policy
}

module "calm_transformer_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_calm_transformer"
  role_names = [module.calm_transformer.task_role_name]

  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "calm_transformer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.calm_transformer_queue.name

  queue_high_actions = [module.calm_transformer.scale_up_arn]
  queue_low_actions  = [module.calm_transformer.scale_down_arn]
}
