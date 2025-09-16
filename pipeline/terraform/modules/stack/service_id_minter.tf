module "id_minter_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_id_minter_output"
  role_names = [module.id_minter_lambda.lambda_role_name]
}

module "id_minter_lambda" {
  source = "../pipeline_lambda"

  pipeline_date = var.pipeline_date
  service_name  = "id_minter"

  environment_variables = {
    topic_arn           = module.id_minter_output_topic.arn
    max_connections     = local.id_minter_task_max_connections
    es_source_index     = local.es_works_source_index
    es_identified_index = local.es_works_identified_index
  }

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.rds_config.security_group_id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  secret_env_vars = merge({
    cluster_url          = "rds/identifiers-serverless/endpoint"
    cluster_url_readonly = "rds/identifiers-serverless/reader_endpoint"
    db_port              = "rds/identifiers-serverless/port"
    db_username          = "catalogue/id_minter/rds_user"
    db_password          = "catalogue/id_minter/rds_password"
    }, local.pipeline_storage_es_service_secrets["id_minter"],
    {
      es_downstream_host     = local.pipeline_storage_private_host
      es_downstream_port     = local.pipeline_storage_port
      es_downstream_protocol = local.pipeline_storage_protocol
      es_downstream_apikey   = local.pipeline_storage_es_service_secrets["id_minter"]["es_apikey"]

      es_upstream_host     = local.pipeline_storage_private_host
      es_upstream_port     = local.pipeline_storage_port
      es_upstream_protocol = local.pipeline_storage_protocol
      es_upstream_apikey   = local.pipeline_storage_es_service_secrets["id_minter"]["es_apikey"]
    }
  )

  timeout = 60 * 5 # 10 Minutes

  queue_config = {
    topic_arns          = local.transformer_output_topic_arns
    max_receive_count   = 3
    maximum_concurrency = 30
    batch_size          = 75

    visibility_timeout_seconds = 60 * 5 # 10 Minutes, same or higher than lambda timeout
    batching_window_seconds    = 60     # How long to wait to accumulate message: 1 minute
  }

  ecr_repository_name = "uk.ac.wellcome/id_minter"
}