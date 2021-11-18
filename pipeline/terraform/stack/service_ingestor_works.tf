locals {
  work_ingestor_flush_interval_seconds = 60
}

module "ingestor_works_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name      = "${local.namespace}_ingestor_works"
  topic_arns      = [module.router_work_output_topic.arn, module.relation_embedder_output_topic.arn]
  alarm_topic_arn = var.dlq_alarm_arn

  max_receive_count = 6

  visibility_timeout_seconds = local.work_ingestor_flush_interval_seconds + 30
}

# Service

module "ingestor_works" {
  source = "../modules/service"

  namespace = local.namespace
  name      = "ingestor_works"

  container_image = local.ingestor_works_image
  security_group_ids = [
    aws_security_group.service_egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = data.aws_ecs_cluster.cluster.id

  env_vars = {
    metrics_namespace = "${local.namespace}_ingestor_works"
    topic_arn         = module.work_ingestor_topic.arn

    es_works_index        = local.es_works_index
    es_denormalised_index = local.es_works_denormalised_index
    es_is_reindexing      = var.is_reindexing

    ingest_queue_id               = module.ingestor_works_queue.url
    ingest_flush_interval_seconds = local.work_ingestor_flush_interval_seconds

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
    ingest_batch_size = var.is_reindexing ? 100 : 10
  }

  secret_env_vars = merge({
    es_host_pipeline_storage     = local.pipeline_storage_private_host
    es_port_pipeline_storage     = local.pipeline_storage_port
    es_protocol_pipeline_storage = local.pipeline_storage_protocol
    es_username_pipeline_storage = "elasticsearch/pipeline_storage_${var.pipeline_date}/work_ingestor/es_username"
    es_password_pipeline_storage = "elasticsearch/pipeline_storage_${var.pipeline_date}/work_ingestor/es_password"
  })

  subnets = var.subnets

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  queue_read_policy = module.ingestor_works_queue.read_policy

  cpu    = 2048
  memory = 4096

  use_fargate_spot = true

  deployment_service_env  = var.release_label
  deployment_service_name = "work-ingestor"
  shared_logging_secrets  = var.shared_logging_secrets
}

# Output topic

module "work_ingestor_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_work_ingestor"
  role_names = [module.ingestor_works.task_role_name]
}

module "ingestor_works_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.ingestor_works_queue.name

  queue_high_actions = [module.ingestor_works.scale_up_arn]
  queue_low_actions  = [module.ingestor_works.scale_down_arn]
}
