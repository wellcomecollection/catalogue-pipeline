locals {
  ingestor_works_flush_interval_seconds = 60
}

module "ingestor_works_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_ingestor_works_output"
  role_names = [module.ingestor_works.task_role_name]
}

module "ingestor_works" {
  source = "../modules/fargate_service"

  name            = "ingestor_works"
  container_image = local.ingestor_works_image

  topic_arns = [
    module.router_work_output_topic.arn,
    module.relation_embedder_output_topic.arn,
  ]

  queue_visibility_timeout_seconds = local.ingestor_works_flush_interval_seconds + 30

  env_vars = {
    topic_arn = module.ingestor_works_output_topic.arn

    es_works_index        = local.es_works_index
    es_denormalised_index = local.es_works_denormalised_index
    es_is_reindexing      = var.reindexing_state.scale_up_tasks

    ingest_flush_interval_seconds = local.ingestor_works_flush_interval_seconds

    # When an ingestor retrieves a batch from the denormalised index,
    # it might OutOfMemoryError when it deserialises the JSON into
    # in-memory Works.
    #
    # This happens when the collection has a lot of large Works,
    # i.e. Works with lots of relations.
    #
    # During a reindex, the batch is likely a mix of deleted/suppressed Works
    # (small) and visible Works (small to large), so it's unlikely to throw
    # an OOM error.  If it does, the service can restart and the next batch
    # will have a different distribution of Works.
    #
    # When we're not reindexing, a batch of large Works can potentially
    # gum up the ingestor: it throws an OOM, the messages get retried,
    # it throws another OOM.  Continue until messages go to the DLQ.
    #
    # To avoid this happening, we reduce the batch size when we're not
    # reindexing.  A smaller batch size is a bit less efficient, but
    # we don't process many messages when not reindexing so this is fine.
    ingest_batch_size = var.reindexing_state.scale_up_tasks ? 100 : 10
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["work_ingestor"]

  cpu    = 2048
  memory = 4096

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

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

  shared_logging_secrets = var.shared_logging_secrets
}
