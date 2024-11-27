module "relation_embedder_lambda_output_topic" {
  source = "../../topic"

  name       = "${var.namespace}_relation_embedder_lambda_output"
  role_names = [module.relation_embedder_lambda.lambda_role_name]
}

module "relation_embedder_lambda" {
  source = "../../pipeline_lambda"

  pipeline_date = var.pipeline_date
  service_name  = "relation_embedder"

  environment_variables = {
    output_topic_arn = module.relation_embedder_lambda_output_topic.arn

    es_merged_index       = var.es_works_merged_index
    es_denormalised_index = var.es_works_denormalised_index

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
