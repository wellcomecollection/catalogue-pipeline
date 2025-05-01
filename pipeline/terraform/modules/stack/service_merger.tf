module "merger_works_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_merger_works_output"
  role_names = [module.merger.task_role_name]
}

module "merger_works_path_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_merger_works_with_path_output"
  role_names = [module.merger.task_role_name]
}

module "merger_works_incomplete_path_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_merger_works_incomplete_path_output"
  role_names = [module.merger.task_role_name]
}

module "merger_images_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_merger_images_output"
  role_names = [module.merger.task_role_name]
}

module "merger" {
  source = "../fargate_service"

  name            = "merger"
  container_image = local.merger_image

  topic_arns = [
    module.matcher_output_topic.arn,
  ]

  # This has to be longer than the `flush_interval_seconds` in the merger.
  # It also has to be long enough for the Work to actually get processed,
  # and some of them are quite big.
  queue_visibility_timeout_seconds = 20 * 60

  env_vars = {
    merger_works_topic_arn             = module.merger_works_output_topic.arn
    merger_paths_topic_arn             = module.merger_works_path_output_topic
    merger_path_concatenator_topic_arn = module.merger_works_incomplete_path_output_topic
    merger_images_topic_arn            = module.merger_images_output_topic.arn

    es_identified_works_index   = local.es_works_identified_index
    es_denormalised_works_index = local.es_works_denormalised_index
    es_initial_images_index     = local.es_images_initial_index

    batch_size             = 50
    flush_interval_seconds = 120
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["merger"]

  cpu    = 2048
  memory = 4096

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
