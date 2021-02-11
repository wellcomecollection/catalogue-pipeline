locals {
  lock_timeout = 240
}

module "matcher_input_queue" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name = "${local.namespace_hyphen}_matcher_input"

  topic_arns = [
    module.id_minter_topic.arn
  ]

  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn

  # The records in the locktable expire after local.lock_timeout
  # The matcher is able to override locks that have expired
  # Wait slightly longer to make sure locks are expired
  visibility_timeout_seconds = local.lock_timeout + 30
  max_receive_count          = 20
}

# Service

module "matcher" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_matcher"
  container_image = local.matcher_image
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
    var.pipeline_storage_security_group_id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  cpu    = 1024
  memory = 2048

  use_fargate_spot = true

  env_vars = {
    queue_url         = module.matcher_input_queue.url
    metrics_namespace = "${local.namespace_hyphen}_matcher"
    topic_arn         = module.matcher_topic.arn

    dynamo_table            = aws_dynamodb_table.matcher_graph_table.id
    dynamo_index            = "work-sets-index"
    dynamo_lock_table       = aws_dynamodb_table.matcher_lock_table.id
    dynamo_lock_table_index = "context-ids-index"

    dynamo_lock_timeout = local.lock_timeout

    es_index                    = local.es_works_identified_index
    read_batch_size             = 100
    read_flush_interval_seconds = 30
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["matcher"]

  subnets           = var.subnets
  max_capacity      = var.max_capacity
  queue_read_policy = module.matcher_input_queue.read_policy

  depends_on = [
    null_resource.elasticsearch_users,
  ]

  deployment_service_env  = var.release_label
  deployment_service_name = "matcher"
  shared_logging_secrets  = var.shared_logging_secrets
}

resource "aws_iam_role_policy" "matcher_graph_readwrite" {
  role   = module.matcher.task_role_name
  policy = data.aws_iam_policy_document.graph_table_readwrite.json
}

resource "aws_iam_role_policy" "matcher_lock_readwrite" {
  role   = module.matcher.task_role_name
  policy = data.aws_iam_policy_document.lock_table_readwrite.json
}

# Output topic

module "matcher_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_matcher"
  role_names = [module.matcher.task_role_name]
}

module "matcher_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.matcher_input_queue.name

  queue_high_actions = [module.matcher.scale_up_arn]
  queue_low_actions  = [module.matcher.scale_down_arn]
}
