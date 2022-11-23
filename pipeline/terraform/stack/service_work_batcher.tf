locals {
  # The purpose of the batcher is to minimise the number of times
  # an individual record is processed, by batching them together when they
  # are expected to modify each other.  This means that choosing parallelism
  # and bundle size is not about maximising throughput, but about ensuring
  # that the largest number of records are processed in the smallest number
  # of executions.

  # During a reindex, the batcher is expected to receive
  # roughly 0.5 million messages in roughly 2 hours.
  # At peak, messages appear at a rate of about 6000 per minute, so
  # waiting for 20 minutes would perfectly align with processing 120000 at a time.
  # Waiting a little longer ensures that the full 120K capacity is used up more often.
  #
  # During normal running, the maximum message count is never expected to be hit,
  # and processing the same Work multiple times would not be a significant extra load.
  # Wait a minute and bundle them up anyway, in case multiple related works are
  # coming through together.
  wait_minutes = var.reindexing_state.scale_up_tasks ? 25 : 1

  # NOTE: SQS in flight limit is 120k
  # See https://aws.amazon.com/sqs/faqs/ "Q: How large can Amazon SQS message queues be?"
  max_processed_paths = var.reindexing_state.scale_up_tasks ? 120000 : 5000
}

module "batcher_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_batcher_output"
  role_names = [module.batcher.task_role_name]
}

module "batcher" {
  source = "../modules/fargate_service"

  name            = "batcher"
  container_image = local.batcher_image

  topic_arns = [
    module.router_path_output_topic.arn,
    module.path_concatenator_output_topic.arn,
  ]

  # Note: this needs to be bigger than the flush_interval_minutes
  queue_visibility_timeout_seconds = (local.wait_minutes + 5) * 60

  env_vars = {
    output_topic_arn = module.batcher_output_topic.arn

    flush_interval_minutes = local.wait_minutes
    max_processed_paths    = local.max_processed_paths

    max_batch_size = 40
  }

  cpu    = 1024
  memory = 2048

  min_capacity = var.min_capacity
  # We need to minimise fragmentation as much as possible, to serve the goal of the batcher.
  # Running multiple batchers in parallel would defeat this purpose.
  max_capacity = 1

  # Unlike all our other tasks, the batcher isn't really set up to cope
  # with unexpected interruptions.
  use_fargate_spot = false

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
