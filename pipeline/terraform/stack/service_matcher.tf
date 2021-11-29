locals {
  lock_timeout = 1 * 60
}

module "matcher_input_queue" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name = "${local.namespace}_matcher_input"

  topic_arns = [
    module.id_minter_topic.arn
  ]

  alarm_topic_arn = var.dlq_alarm_arn

  # The records in the locktable expire after local.lock_timeout
  # The matcher is able to override locks that have expired
  # Wait slightly longer to make sure locks are expired
  visibility_timeout_seconds = local.lock_timeout + 30

  # Epistemic status of this comment: somewhat speculative.
  #
  # We want a lot of redrives here so we can handle cases where a bunch
  # of works are being merged together in quick succession.
  #
  # Consider: ten works A, B, C, …, J are all going to be merged together,
  # and they arrive at the matcher in that order.
  #
  # The matcher starts processing work A, and acquires a lock on it.
  # The nine other works try to get a lock on A, fail, get redriven.
  #
  # When the visibility timeout expires, the matcher starts processing B
  # and acquires a lock on A.  The eight other works try to get a lock on A,
  # fail, get redriven.
  max_receive_count = 10
}

# Service

module "matcher" {
  source = "../modules/service"

  namespace = local.namespace
  name      = "matcher"

  container_image = local.matcher_image
  security_group_ids = [
    aws_security_group.service_egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  cpu    = 1024
  memory = 2048

  use_fargate_spot = true

  env_vars = {
    queue_url         = module.matcher_input_queue.url
    metrics_namespace = "${local.namespace_hyphen}_matcher"
    topic_arn         = module.matcher_output_topic.arn

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

  subnets = var.subnets

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  queue_read_policy = module.matcher_input_queue.read_policy

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

module "matcher_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_matcher_output"
  role_names = [module.matcher.task_role_name]
}

module "matcher_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.matcher_input_queue.name

  queue_high_actions = [module.matcher.scale_up_arn]
  queue_low_actions  = [module.matcher.scale_down_arn]
}
