module "calm_windows_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name                 = "calm-windows"
  topic_arns                 = [aws_sns_topic.calm_windows_topic.arn]
  alarm_topic_arn            = local.dlq_alarm_arn
  visibility_timeout_seconds = 10800
}

module "adapter_worker" {
  source = "../../infrastructure/modules/worker"

  name = "calm_adapter"

  image = local.calm_adapter_image

  env_vars = {
    calm_api_url          = local.calm_api_url
    calm_sqs_url          = module.calm_windows_queue.url
    calm_sns_topic        = module.calm_adapter_topic.arn
    vhs_dynamo_table_name = module.vhs.table_name
    vhs_bucket_name       = module.vhs.bucket_name
  }
  secret_env_vars = {
    calm_api_username = "calm_adapter/calm_api/username"
    calm_api_password = "calm_adapter/calm_api/password"
    suppressed_fields = "calm_adapter/suppressed_fields"
  }

  min_capacity = 0
  max_capacity = 2

  cpu    = 512
  memory = 1024

  cluster_name             = aws_ecs_cluster.cluster.name
  cluster_arn              = aws_ecs_cluster.cluster.arn
  subnets                  = local.private_subnets
  shared_logging_secrets   = local.shared_logging_secrets
  elastic_cloud_vpce_sg_id = local.elastic_cloud_vpce_sg_id

  security_group_ids = [
    aws_security_group.egress.id,
  ]

  use_fargate_spot = true
}

resource "aws_iam_role_policy" "read_from_adapter_queue" {
  role   = module.adapter_worker.task_role_name
  policy = module.calm_windows_queue.read_policy
}

module "adapter_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.calm_windows_queue.name

  queue_high_actions = [
    module.adapter_worker.scale_up_arn
  ]

  queue_low_actions = [
    module.adapter_worker.scale_down_arn
  ]
}
