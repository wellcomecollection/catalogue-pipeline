module "relation_embedder_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_relation_embedder_output"
  role_names = [module.relation_embedder.task_role_name]
}

module "relation_embedder" {
  source = "../modules/fargate_service"

  name            = "relation_embedder"
  container_image = local.relation_embedder_image

  topic_arns = [
    module.batcher_output_topic.arn,
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

  # Below this line is boilerplate that should be the same across
  # all Fargate services.
  egress_security_group_id             = aws_security_group.egress.id
  elastic_cloud_vpce_security_group_id = var.network_config.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.id

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  dlq_alarm_topic_arn = var.dlq_alarm_arn

  subnets = var.network_config.subnets

  namespace = local.namespace

  deployment_service_env = var.release_label

  shared_logging_secrets = var.logging_config.shared_secrets
}
