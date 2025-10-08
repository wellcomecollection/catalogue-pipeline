locals {
  lock_timeout = 1 * 60

  # The records in the locktable expire after local.lock_timeout
  # The matcher is able to override locks that have expired
  # Wait slightly longer to make sure locks are expired
  queue_visibility_timeout_seconds = local.lock_timeout + 30

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

resource "aws_iam_role_policy" "matcher_graph_readwrite" {
  role   = module.matcher_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.graph_table_readwrite.json
}

resource "aws_iam_role_policy" "matcher_lock_readwrite" {
  role   = module.matcher_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.lock_table_readwrite.json
}

module "matcher_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_matcher_output"
  role_names = [module.matcher_lambda.lambda_role_name]
}

module "matcher_lambda" {
  source = "../pipeline_lambda"

  pipeline_date = var.pipeline_date

  service_name = "matcher"

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  environment_variables = {
    topic_arn = module.matcher_output_topic.arn

    dynamo_table            = aws_dynamodb_table.matcher_graph_table.id
    dynamo_index            = "work-sets-index"
    dynamo_lock_table       = aws_dynamodb_table.matcher_lock_table.id
    dynamo_lock_table_index = "context-ids-index"

    dynamo_lock_timeout = local.lock_timeout

    es_index = local.es_works_identified_index
  }

  secret_env_vars = module.elastic.pipeline_storage_es_service_secrets["matcher"]

  ecr_repository_name = "uk.ac.wellcome/matcher"
  queue_config = {
    visibility_timeout_seconds = local.queue_visibility_timeout_seconds
    max_receive_count          = local.max_receive_count
    batching_window_seconds    = 30
    batch_size                 = 100
    topic_arns = [
      module.id_minter_output_topic.arn,
    ]
  }
}
