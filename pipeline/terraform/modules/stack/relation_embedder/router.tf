module "router_path_output_topic" {
  source = "../../topic"

  name       = "${var.namespace}_router_path_output"
  role_names = [module.router.task_role_name]
}

module "router_candidate_incomplete_paths_output_topic" {
  source = "../../topic"

  name       = "${var.namespace}_router_candidate_incomplete_paths_output"
  role_names = [module.router.task_role_name]
}

module "router_work_output_topic" {
  source = "../../topic"

  name       = "${var.namespace}_router_work_output"
  role_names = [module.router.task_role_name]
}

module "router" {
  source = "../../fargate_service"

  name            = "${var.namespace}_router"
  container_image = var.router_image

  topic_arns = [
    var.router_input_topic_arn,
  ]

  env_vars = {
    queue_parallelism = 10

    paths_topic_arn             = module.router_path_output_topic.arn
    path_concatenator_topic_arn = module.router_candidate_incomplete_paths_output_topic.arn
    works_topic_arn             = module.router_work_output_topic.arn

    es_merged_index       = var.es_works_merged_index
    es_denormalised_index = var.es_works_denormalised_index
    batch_size            = 100
    # The flush interval must be sufficiently lower than the message timeout
    # to allow the messages to be processed after the flush interval but before
    # they expire.  The upstream queue timeout is not set by us, leaving it
    # at the default 30 seconds.
    # See https://github.com/wellcomecollection/platform/issues/5463
    flush_interval_seconds = 20
  }

  secret_env_vars = var.pipeline_storage_es_service_secrets["router"]

  cpu    = 1024
  memory = 2048

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  fargate_service_boilerplate = var.fargate_service_boilerplate
}
