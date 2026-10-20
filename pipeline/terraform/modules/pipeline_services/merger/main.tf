// dead topic, the image inferrer now runs on a schedule 
// the merger code still references it so it stays here for now 
// output has been removed from the module
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

  # The flag is left out entirely when off, so existing pipelines see no change.
  environment_variables = merge(
    {
      merger_images_topic_arn = module.merger_images_output_topic.arn

      es_identified_works_index   = var.es_works_identified_index
      es_denormalised_works_index = var.es_works_denormalised_index
      es_initial_images_index     = var.es_images_initial_index
    },
    var.batch_indexing ? { merger_batch_indexing = "true" } : {}
  )

  secret_env_vars = merge(
    local.es_upstream_config,
    local.es_downstream_config
  )

  ecr_repository_name = "uk.ac.wellcome/merger"

  queue_config = var.queue_config
}
