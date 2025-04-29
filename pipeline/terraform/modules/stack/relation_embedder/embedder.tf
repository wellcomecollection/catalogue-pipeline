locals {
  maximum_concurrency = var.reindexing_state.scale_up_tasks ? 10 : 3
}

module "embedder_lambda_output_topic" {
  source = "../../topic"

  name       = "${local.topic_namespace}_embedder_lambda_output"
  role_names = [module.embedder_lambda.lambda_role_name]
}

module "embedder_lambda" {
  source = "../../pipeline_lambda"

  pipeline_date = var.pipeline_date
  service_name  = "${var.namespace}_embedder"

  vpc_config = var.lambda_vpc_config

  environment_variables = {
    output_topic_arn = module.embedder_lambda_output_topic.arn

    es_denormalised_index = var.es_works_denormalised_index

    affected_works_scroll_size   = 50 // NOTE: limit to avoid memory errors
    complete_tree_scroll_size    = 800
    index_batch_size             = 100 // NOTE: too large results in 413 from ES
    index_flush_interval_seconds = 60
  }

  secret_env_vars = var.pipeline_storage_es_service_secrets["relation_embedder"]

  # see comment on fargate service's queue_visibility_timeout_seconds
  # 15 minutes is the max for lambda, is it going to be enough?
  timeout     = 60 * 15 # 15 Minutes
  memory_size = 4096

  queue_config = {
    topic_arns = [
      module.batcher_lambda_output_topic.arn
    ]

    batching_window_seconds = 60                        # How long to wait to accumulate message: 1 minute
    maximum_concurrency     = local.maximum_concurrency # number of messages in flight is max_capacity x queue_parallelism

    visibility_timeout_seconds = 60 * 15 # same or higher than lambda timeout

    max_receive_count = 3
    batch_size        = 10
  }

  ecr_repository_name = "uk.ac.wellcome/relation_embedder"
}
