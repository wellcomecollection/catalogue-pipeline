module "relation_embedder_sub" {
  source = "./relation_embedder"

  namespace        = "r_embed"
  pipeline_date    = var.pipeline_date
  reindexing_state = var.reindexing_state

  es_works_denormalised_index         = local.es_works_denormalised_index
  pipeline_storage_es_service_secrets = local.pipeline_storage_es_service_secrets

  # path_concatenator
  path_concatenator_image           = local.path_concatenator_image
  path_concatenator_input_topic_arn = module.merger_works_incomplete_path_output_topic.arn

  # batcher
  batcher_input_topic_arn = module.merger_works_path_output_topic.arn

  # ecs services config
  min_capacity                = var.min_capacity
  max_capacity                = var.reindexing_state.scale_up_tasks ? var.max_capacity : min(1, var.max_capacity)
  fargate_service_boilerplate = local.fargate_service_boilerplate
  lambda_vpc_config           = local.lambda_vpc_config
}
