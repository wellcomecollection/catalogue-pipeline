locals {
  wait_minutes = var.reindexing_state.scale_up_tasks ? 45 : 1

  # NOTE: SQS in flight limit is 120k
  max_processed_paths = var.reindexing_state.scale_up_tasks ? 100000 : 5000
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
  max_capacity = min(1, local.max_capacity)

  # Unlike all our other tasks, the batcher isn't really set up to cope
  # with unexpected interruptions.
  use_fargate_spot = false

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
