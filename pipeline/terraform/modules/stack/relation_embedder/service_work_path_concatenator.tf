
module "path_concatenator_output_topic" {
  source = "../../topic"

  name       = "${var.namespace}_path_concatenator_output_topic"
  role_names = [module.path_concatenator.task_role_name]
}

module "path_concatenator" {
  source = "../../fargate_service"

  name            = "path_concatenator"
  container_image = var.path_concatenator_image

  topic_arns = [
    module.router_candidate_incomplete_paths_output_topic.arn,
  ]

  env_vars = {
    queue_parallelism = 10

    downstream_topic_arn = module.path_concatenator_output_topic.arn

    es_merged_index = var.es_works_merged_index
  }

  secret_env_vars = var.pipeline_storage_es_service_secrets["path_concatenator"]

  cpu    = 1024
  memory = 2048

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  fargate_service_boilerplate = var.fargate_service_boilerplate
}
