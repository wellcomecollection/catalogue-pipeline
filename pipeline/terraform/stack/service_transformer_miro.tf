module "miro_transformer_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_miro_transformer"
  topic_arns      = var.miro_adapter_topic_arns
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

module "miro_transformer" {
  source          = "../modules/pipeline_service"
  service_name    = "${local.namespace_hyphen}_miro_transformer"
  container_image = local.transformer_miro_image
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id

  env_vars = {
    sns_arn              = module.miro_transformer_topic.arn
    transformer_queue_id = module.miro_transformer_queue.url
    metrics_namespace    = "${local.namespace_hyphen}_miro_transformer"
    messages_bucket_name = aws_s3_bucket.messages.id
    logstash_host        = local.logstash_host
  }

  secret_env_vars = {}

  subnets             = var.subnets
  aws_region          = var.aws_region
  max_capacity        = 10
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.miro_transformer_queue.read_policy
}

resource "aws_iam_role_policy" "miro_transformer_vhs_miro_adapter_read" {
  role   = module.miro_transformer.task_role_name
  policy = var.vhs_miro_read_policy
}

module "miro_transformer_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_miro_transformer"
  role_names = [module.miro_transformer.task_role_name]

  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "miro_transformer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = module.miro_transformer_queue.name

  queue_high_actions = [module.miro_transformer.scale_up_arn]
  queue_low_actions  = [module.miro_transformer.scale_down_arn]
}
