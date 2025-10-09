module "merger_works_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_merger_works_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_works_path_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_merger_works_with_path_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_works_incomplete_path_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_merger_works_incomplete_path_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_images_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_merger_images_output"
  role_names = [module.merger_lambda.lambda_role_name]
}

module "merger_lambda" {
  source = "../pipeline_lambda"

  pipeline_date = var.pipeline_date

  service_name = "merger"

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

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
    module.elastic.pipeline_storage_es_service_secrets["merger"], // old config, to be removed
    {
      es_upstream_host     = module.elastic.pipeline_storage_private_host
      es_upstream_port     = module.elastic.pipeline_storage_port
      es_upstream_protocol = module.elastic.pipeline_storage_protocol
      es_upstream_apikey   = module.elastic.pipeline_storage_es_service_secrets["merger"]["es_apikey"]

      es_downstream_host     = module.elastic.pipeline_storage_private_host
      es_downstream_port     = module.elastic.pipeline_storage_port
      es_downstream_protocol = module.elastic.pipeline_storage_protocol
      es_downstream_apikey   = module.elastic.pipeline_storage_es_service_secrets["merger"]["es_apikey"]
    }
  )

  ecr_repository_name = "uk.ac.wellcome/merger"
  queue_config = {
    visibility_timeout_seconds = local.queue_visibility_timeout_seconds
    max_receive_count          = local.max_receive_count
    batching_window_seconds    = 120
    batch_size                 = 50
    topic_arns = [
      module.matcher_output_topic.arn,
    ]
  }
}
