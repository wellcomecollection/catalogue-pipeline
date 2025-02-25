module "id_minter_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_id_minter_output"
  role_names = [module.id_minter.task_role_name]
}

module "id_minter" {
  source = "../fargate_service"

  name            = "id_minter"
  container_image = local.id_minter_image

  // Override entrypoint & command to dual use lambda container image
  // This should be removed once we have a dedicated batcher_lambda image
  entrypoint = [
    "/opt/docker/bin/main"
  ]
  command = null

  security_group_ids = [
    local.rds_config.security_group_id,
  ]

  topic_arns = local.transformer_output_topic_arns

  queue_visibility_timeout_seconds = 120

  # We've seen issues where the ID minter is failing messages for
  # no discernable reason; given the visibility timeout is greater than
  # the default cooldown period, it's possible it's being scaled away
  # too quickly when it has works with lots of identifiers.
  cooldown_period = "15m"

  env_vars = {
    topic_arn                     = module.id_minter_output_topic.arn
    max_connections               = local.id_minter_task_max_connections
    es_source_index               = local.es_works_source_index
    es_identified_index           = local.es_works_identified_index
    ingest_batch_size             = 75
    ingest_flush_interval_seconds = 30
  }

  secret_env_vars = merge({
    cluster_url          = "rds/identifiers-serverless/endpoint"
    cluster_url_readonly = "rds/identifiers-serverless/reader_endpoint"
    db_port              = "rds/identifiers-serverless/port"
    db_username          = "catalogue/id_minter/rds_user"
    db_password          = "catalogue/id_minter/rds_password"
  }, local.pipeline_storage_es_service_secrets["id_minter"])

  cpu    = 2048
  memory = 4096

  // TODO: Temporary disable the ECS id_minter, while we ensure the lambda is working
  // Delete this block once we are confident the lambda is working.
  min_capacity = 0 //var.min_capacity
  max_capacity = 0 //local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}

module "id_minter_lambda" {
  source = "../pipeline_lambda"

  pipeline_date = var.pipeline_date
  service_name  = "${local.namespace}_id_minter"

  environment_variables = {
    topic_arn                     = module.id_minter_output_topic.arn
    max_connections               = local.id_minter_task_max_connections
    es_source_index               = local.es_works_source_index
    es_identified_index           = local.es_works_identified_index
  }

  secret_env_vars = merge({
    cluster_url          = "rds/identifiers-serverless/endpoint"
    cluster_url_readonly = "rds/identifiers-serverless/reader_endpoint"
    db_port              = "rds/identifiers-serverless/port"
    db_username          = "catalogue/id_minter/rds_user"
    db_password          = "catalogue/id_minter/rds_password"
  }, local.pipeline_storage_es_service_secrets["id_minter"])

  timeout = 60 * 5 # 10 Minutes

  queue_config = {
    topic_arns = local.transformer_output_topic_arns
    max_receive_count   = 3
    maximum_concurrency = 10
    batch_size          = 75

    visibility_timeout_seconds = 60 * 5 # 10 Minutes, same or higher than lambda timeout
    batching_window_seconds    = 60     # How long to wait to accumulate message: 1 minute
  }

  ecr_repository_name = "uk.ac.wellcome/id_minter"
}
