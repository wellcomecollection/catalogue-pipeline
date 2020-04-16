module "recorder_queue" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name = "${local.namespace_hyphen}_recorder"
  topic_arns = [
    module.miro_transformer_topic.arn,
    module.sierra_transformer_topic.arn,
    module.mets_transformer_topic.arn,
    module.calm_transformer_topic.arn,
  ]
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

module "recorder" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_recorder"
  container_image = local.recorder_image
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id

  env_vars = {
    recorder_queue_url             = module.recorder_queue.url
    metrics_namespace              = "${local.namespace_hyphen}_recorder"
    vhs_recorder_dynamo_table_name = module.vhs_recorder.table_name
    vhs_recorder_bucket_name       = module.vhs_recorder.bucket_name
    sns_topic                      = module.recorder_topic.arn
    logstash_host                  = local.logstash_host
  }

  secret_env_vars = {}

  subnets             = var.subnets
  aws_region          = var.aws_region
  max_capacity        = 10
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.recorder_queue.read_policy
}

resource "aws_iam_role_policy" "recorder_vhs_recorder_readwrite" {
  role   = module.recorder.task_role_name
  policy = module.vhs_recorder.full_access_policy
}

module "recorder_topic" {
  source = "../modules/topic"

  name                = "${local.namespace_hyphen}_recorder"
  role_names          = [module.recorder.task_role_name]
  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "recorder_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=8b53ad48ca041851c52d2b8c6f1f9ceba926ef6c"
  queue_name = module.recorder_queue.name

  queue_high_actions = [module.recorder.scale_up_arn]
  queue_low_actions  = [module.recorder.scale_down_arn]
}
