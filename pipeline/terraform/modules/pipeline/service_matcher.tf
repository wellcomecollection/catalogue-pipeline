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

module "matcher" {
  source = "./matcher"

  pipeline_date             = var.pipeline_date
  es_works_identified_index = local.es_works_identified_index
  lock_timeout              = local.lock_timeout
  scale_up_matcher_db       = var.reindexing_state.scale_up_matcher_db

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  secret_env_vars = module.elastic.pipeline_storage_es_service_secrets["matcher"]

  queue_config = {
    visibility_timeout_seconds = local.queue_visibility_timeout_seconds
    max_receive_count          = local.max_receive_count
    batching_window_seconds    = 30
    batch_size                 = var.reindexing_state.scale_up_matcher_db ? 400 : 100
    maximum_concurrency        = var.reindexing_state.scale_up_matcher_db ? 40 : 2
    topic_arns = [
      module.id_minter_lambda.id_minter_output_topic_arn,
    ]
  }

  timeout     = var.reindexing_state.scale_up_matcher_db ? 300 : 30
  memory_size = var.reindexing_state.scale_up_matcher_db ? 4096 : 1024
}

# ──────────────────────────────────────────────
# State moves for extracting matcher into sub-module
# ──────────────────────────────────────────────

moved {
  from = aws_dynamodb_table.matcher_graph_table
  to   = module.matcher.aws_dynamodb_table.graph_table
}

moved {
  from = aws_dynamodb_table.matcher_lock_table
  to   = module.matcher.aws_dynamodb_table.lock_table
}

moved {
  from = module.matcher_output_topic
  to   = module.matcher.module.matcher_output_topic
}

moved {
  from = module.matcher_lambda
  to   = module.matcher.module.matcher_lambda
}

moved {
  from = aws_iam_role_policy.matcher_graph_readwrite
  to   = module.matcher.aws_iam_role_policy.matcher_graph_readwrite
}

moved {
  from = aws_iam_role_policy.matcher_lock_readwrite
  to   = module.matcher.aws_iam_role_policy.matcher_lock_readwrite
}
