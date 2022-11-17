module "calm_indexer_queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"

  queue_name = "calm-indexer-input"
  topic_arns = [
    module.calm_adapter_topic.arn,
    local.calm_reporting_topic_arn
  ]

  alarm_topic_arn = local.dlq_alarm_arn
}

module "calm_indexer" {
  source = "../../infrastructure/modules/worker"

  name = "calm_indexer"

  image = local.calm_indexer_image

  min_capacity = 0
  max_capacity = 1

  env_vars = {
    sqs_queue_url     = module.calm_indexer_queue.url
    es_index          = "calm_catalog"
    metrics_namespace = "calm_indexer"

    es_username = "calm_indexer"
    es_protocol = "https"
    es_port     = "9243"
  }

  secret_env_vars = {
    es_password = "reporting/calm_indexer/es_password"
    es_host     = "reporting/es_host"
  }

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

resource "aws_iam_role_policy" "read_from_indexer_q" {
  role   = module.calm_indexer.task_role_name
  policy = module.calm_indexer_queue.read_policy
}

resource "aws_iam_role_policy" "indexer_read_from_vhs" {
  role   = module.calm_indexer.task_role_name
  policy = module.vhs.read_policy
}

module "indexer_scaling" {
  source     = "github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.calm_indexer_queue.name

  queue_high_actions = [
    module.calm_indexer.scale_up_arn
  ]

  queue_low_actions = [
    module.calm_indexer.scale_down_arn
  ]
}
