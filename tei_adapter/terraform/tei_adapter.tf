module "tei_adapter_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name                 = "tei-adapter"
  topic_arns                 = [module.tei_id_extractor_topic.arn]
  alarm_topic_arn            = local.dlq_alarm_arn
  visibility_timeout_seconds = 60
}

module "tei_adapter" {
  source = "../../infrastructure/modules/worker"

  name = "tei_adapter"

  image = local.tei_adapter_image

  env_vars = {
    metrics_namespace        = "${local.namespace}_tei_adapter"
    queue_url                = module.tei_adapter_queue.url
    topic_arn                = module.tei_adapter_topic.arn
    parallelism              = 10
    delete_delay             = "2 minutes"
    tei_adapter_dynamo_table = aws_dynamodb_table.tei_adapter_table.id
  }

  min_capacity = local.min_capacity
  max_capacity = local.max_capacity

  cpu    = 1024
  memory = 2048

  cluster_name             = aws_ecs_cluster.cluster.name
  cluster_arn              = aws_ecs_cluster.cluster.arn
  subnets                  = local.private_subnets
  shared_logging_secrets   = local.shared_logging_secrets
  elastic_cloud_vpce_sg_id = local.elastic_cloud_vpce_sg_id

  security_group_ids = [
    aws_security_group.egress.id,
    aws_security_group.rds_ingress_security_group.id
  ]

  deployment_service_env  = local.release_label
  deployment_service_name = "tei-adapter"

  use_fargate_spot = true
}

resource "aws_iam_role_policy" "read_from_adapter_queue" {
  role   = module.tei_adapter.task_role_name
  policy = module.tei_adapter_queue.read_policy
}

resource "aws_iam_role_policy" "tei_adapter_publish_policy" {
  role   = module.tei_adapter.task_role_name
  policy = module.tei_adapter_topic.publish_policy
}

resource "aws_iam_role_policy" "tei_adapter_dynamo_full_access" {
  role   = module.tei_adapter.task_role_name
  policy = data.aws_iam_policy_document.tei_dynamo_full_access_policy.json
}

module "tei_adapter_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.tei_adapter_queue.name

  queue_high_actions = [
    module.tei_adapter.scale_up_arn
  ]

  queue_low_actions = [
    module.tei_adapter.scale_down_arn
  ]
}
