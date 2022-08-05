locals {
  ingestor_images_flush_interval_seconds = 30
}

module "ingestor_images_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_ingestor_images_output"
  role_names = [module.ingestor_images.task_role_name]
}

module "ingestor_images" {
  source = "../modules/fargate_service"

  name            = "ingestor_images"
  container_image = local.ingestor_images_image

  topic_arns = [
    module.image_inferrer_output_topic.arn,
  ]

  queue_visibility_timeout_seconds = local.ingestor_images_flush_interval_seconds + 60

  env_vars = {
    topic_arn = module.ingestor_images_output_topic.arn

    es_images_index    = local.es_images_index
    es_augmented_index = local.es_images_augmented_index
    es_is_reindexing   = var.reindexing_state.scale_up_tasks

    ingest_flush_interval_seconds = local.ingestor_images_flush_interval_seconds

    # We initially had this set to 100, and we saw errors like:
    #
    #     com.sksamuel.elastic4s.http.JavaClientExceptionWrapper:
    #     org.apache.http.ContentTooLongException: entity content is too long
    #     [130397743] for the configured buffer limit [104857600]
    #
    # My guess is that turning down the batch size will sort out these
    # errors, because I think this error is caused by getting a response
    # that's >100MB.
    #
    # I cranked it down to 50, still saw the error sometimes.
    #
    # See https://github.com/wellcomecollection/platform/issues/5038
    ingest_batch_size = 10
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["image_ingestor"]

  cpu    = 512
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

  shared_logging_secrets = var.logging_config.shared_secrets
}
