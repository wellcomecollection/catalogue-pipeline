locals {
  # The purpose of the batcher is to minimise the number of times
  # an individual record is processed, by batching them together when they
  # are expected to modify each other.  This means that choosing parallelism
  # and bundle size is not about maximising throughput, but about ensuring
  # that the largest number of records are processed in the smallest number
  # of executions.

  # During a reindex, the batcher is expected to receive
  # roughly 0.5 million messages in roughly 2 hours & at peak,
  # messages appear at a rate of about 6000 per minute.

  # These values are chosen to ensure that the batcher can keep up.

  lambda_timeout_seconds = 60 * 10 # 10 Minutes
  # This value should be higher than or equal to the lambda timeout, to avoid messages being reprocessed.
  lamda_q_vis_timeout_seconds = local.lambda_timeout_seconds
  # How long to wait to accumulate message: 5 minutes during reindexing, 1 minute otherwise
  batcher_batching_window_seconds = var.reindexing_state.scale_up_tasks ? (60 * 5) : 60
}

module "batcher_lambda_output_topic" {
  source = "../../topic"

  name       = "${local.topic_namespace}_batcher_lambda_output"
  role_names = [module.batcher_lambda.lambda_role_name]
}

module "batcher_lambda" {
  source = "../../pipeline_lambda"

  pipeline_date = var.pipeline_date
  service_name  = "${var.namespace}_batcher"

  environment_variables = {
    output_topic_arn = module.batcher_lambda_output_topic.arn
    max_batch_size   = 40

    # These are unused by the lambda, but are required in the environment
    flush_interval_minutes = 0
    max_processed_paths    = 0
  }

  timeout = local.lambda_timeout_seconds

  queue_config = {
    topic_arns = [
      var.batcher_input_topic_arn,
      module.path_concatenator_output_topic.arn,
    ]
    max_receive_count   = 1
    maximum_concurrency = 20
    batch_size          = 10000

    visibility_timeout_seconds = local.lamda_q_vis_timeout_seconds
    batching_window_seconds    = local.batcher_batching_window_seconds
  }

  ecr_repository_name = "uk.ac.wellcome/batcher"
}
