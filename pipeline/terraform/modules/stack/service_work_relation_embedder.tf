module "relation_embedder_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_relation_embedder_output"
  role_names = [module.relation_embedder.task_role_name]
}

module "relation_embedder_lambda_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_relation_embedder_lambda_output"
  role_names = [module.relation_embedder_lambda.lambda_role_name]
}

module "relation_embedder_lambda" {
  source = "../pipeline_lambda"

  pipeline_date = var.pipeline_date
  service_name  = "relation_embedder"

  environment_variables = {
    output_topic_arn = module.relation_embedder_lambda_output_topic.arn

    es_merged_index       = local.es_works_merged_index
    es_denormalised_index = local.es_works_denormalised_index

    affected_works_scroll_size   = 50 // NOTE: limit to avoid memory errors
    complete_tree_scroll_size    = 800
    index_batch_size             = 100 // NOTE: too large results in 413 from ES
    index_flush_interval_seconds = 60
  }
  # see comment on fargate service's queue_visibility_timeout_seconds
  # 15 minutes is the max for lambda, is it going to be enough?
  timeout = 60 * 15 # 15 Minutes

  queue_config = {
    topic_arns = [
      module.batcher_lambda_output_topic.arn
    ]

    maximum_concurrency        = 10      # could we go up to 30? ie. service's max_capacity x queue_parallelism
    visibility_timeout_seconds = 60 * 15 # same or higher than lambda timeout
  }

  ecr_repository_name = "uk.ac.wellcome/batcher"
}

module "relation_embedder" {
  source = "../fargate_service"

  name            = "relation_embedder"
  container_image = local.relation_embedder_image

  topic_arns = [
    module.batcher_lambda_output_topic.arn
  ]

  # We know that 10 minutes is too short; some big archives can't be
  # processed in that time, and they end up on a DLQ.
  queue_visibility_timeout_seconds = 30 * 60

  env_vars = {
    topic_arn = module.relation_embedder_output_topic.arn

    es_merged_index       = local.es_works_merged_index
    es_denormalised_index = local.es_works_denormalised_index

    queue_parallelism            = 3  // NOTE: limit to avoid memory errors
    affected_works_scroll_size   = 50 // NOTE: limit to avoid memory errors
    complete_tree_scroll_size    = 800
    index_batch_size             = 100 // NOTE: too large results in 413 from ES
    index_flush_interval_seconds = 60
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["relation_embedder"]

  cpu    = 2048
  memory = 4096

  # NOTE: limit to avoid >500 concurrent scroll contexts
  min_capacity = var.min_capacity
  max_capacity = min(10, local.max_capacity)

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
