module "merger_works_output_topic" {
  source = "../../topic"

  name       = "${local.namespace}_merger_works_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_works_path_output_topic" {
  source = "../../topic"

  name       = "${local.namespace}_merger_works_with_path_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_works_incomplete_path_output_topic" {
  source = "../../topic"

  name       = "${local.namespace}_merger_works_incomplete_path_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_images_output_topic" {
  source = "../../topic"

  name       = "${local.namespace}_merger_images_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_lambda" {
  source = "../../pipeline_lambda"

  service_name  = "merger${local.namespace_suffix}"
  pipeline_date = var.pipeline_date
  vpc_config    = var.vpc_config

  environment_variables = {
    merger_works_topic_arn             = module.merger_works_output_topic.arn
    merger_paths_topic_arn             = module.merger_works_path_output_topic.arn
    merger_path_concatenator_topic_arn = module.merger_works_incomplete_path_output_topic.arn
    merger_images_topic_arn            = module.merger_images_output_topic.arn

    es_identified_works_index   = local.es_works_identified_index
    es_denormalised_works_index = local.es_works_denormalised_index
    es_initial_images_index     = local.es_images_initial_index
  }

  secret_env_vars = merge(
    local.es_upstream_config,
    local.es_downstream_config
  )

  ecr_repository_name = "uk.ac.wellcome/merger"

  queue_config = var.queue_config
}
