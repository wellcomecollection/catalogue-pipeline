module "merger_images_output_topic" {
  source = "../../topic"

  name       = "${local.namespace}_images_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_lambda" {
  source = "../../pipeline_lambda"

  service_name  = var.service_name
  pipeline_date = var.pipeline_date
  vpc_config    = var.vpc_config

  environment_variables = {
    merger_images_topic_arn = module.merger_images_output_topic.arn

    es_identified_works_index   = var.es_works_identified_index
    es_denormalised_works_index = var.es_works_denormalised_index
    es_initial_images_index     = var.es_images_initial_index
  }

  secret_env_vars = merge(
    local.es_upstream_config,
    local.es_downstream_config
  )

  ecr_repository_name = "uk.ac.wellcome/merger"

  queue_config = var.queue_config
}
