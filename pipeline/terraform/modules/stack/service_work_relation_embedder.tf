module "relation_embedder_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_relation_embedder_output"
  role_names = [module.relation_embedder.task_role_name]
}

module "relation_embedder" {
  source = "../fargate_service"

  name            = "relation_embedder"
  container_image = local.relation_embedder_image

  topic_arns = [
    module.batcher_output_topic.arn
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
