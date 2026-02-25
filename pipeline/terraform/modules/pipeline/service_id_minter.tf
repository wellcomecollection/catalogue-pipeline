module "id_minter_step_function_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_id_minter_step_function_output"
  role_names = [module.id_minter_lambda_step_function.lambda_role_name]
}

locals {
  id_minter_step_function_environment_variables = {
    topic_arn           = module.id_minter_step_function_output_topic.arn
    max_connections     = local.id_minter_task_max_connections
    es_source_index     = local.es_works_source_index
    es_identified_index = local.es_works_identified_index
  }

  id_minter_vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.rds_config.security_group_id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  id_minter_secret_env_vars = merge({
    cluster_url          = "rds/identifiers-serverless/endpoint"
    cluster_url_readonly = "rds/identifiers-serverless/reader_endpoint"
    db_port              = "rds/identifiers-serverless/port"
    db_username          = "catalogue/id_minter/rds_user"
    db_password          = "catalogue/id_minter/rds_password"
    }, module.elastic.pipeline_storage_es_service_secrets["id_minter"],
    {
      es_downstream_host     = module.elastic.pipeline_storage_private_host
      es_downstream_port     = module.elastic.pipeline_storage_port
      es_downstream_protocol = module.elastic.pipeline_storage_protocol
      es_downstream_apikey   = module.elastic.pipeline_storage_es_service_secrets["id_minter"]["es_apikey"]

      es_upstream_host     = module.elastic.pipeline_storage_private_host
      es_upstream_port     = module.elastic.pipeline_storage_port
      es_upstream_protocol = module.elastic.pipeline_storage_protocol
      es_upstream_apikey   = module.elastic.pipeline_storage_es_service_secrets["id_minter"]["es_apikey"]
    }
  )
}

module "id_minter_lambda_step_function" {
  source = "../pipeline_lambda"

  pipeline_date = var.pipeline_date
  service_name  = "id_minter_step_function"

  environment_variables = local.id_minter_step_function_environment_variables
  vpc_config            = local.id_minter_vpc_config
  secret_env_vars       = local.id_minter_secret_env_vars

  memory_size = 4096

  timeout = 60 * 5 # 10 Minutes

  image_config = {
    command = ["weco.pipeline.id_minter.StepFunctionMain::handleRequest"]
  }

  ecr_repository_name = "uk.ac.wellcome/id_minter"
}

locals {
  id_minter_v2_vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.rds_v2_config.security_group_id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  id_minter_v2_env_vars = {
    RDS_MAX_CONNECTIONS = local.id_minter_task_max_connections
    LOG_LEVEL           = "DEBUG"
  }

  id_minter_v2_secret_env_vars = {
    RDS_PRIMARY_HOST = "rds/identifiers-v2-serverless/endpoint"
  }
}

# This is the new version of the id_minter, that uses the V2 RDS cluster
module "id_minter_lambda" {
  source = "./id_minter"

  pipeline_date             = var.pipeline_date
  id_minter_vpc_config      = local.id_minter_v2_vpc_config
  id_minter_env_vars        = local.id_minter_v2_env_vars
  id_minter_secret_env_vars = local.id_minter_v2_secret_env_vars
}