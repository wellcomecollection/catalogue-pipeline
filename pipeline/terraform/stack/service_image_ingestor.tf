locals {
  image_ingestor_flush_interval_seconds = 30
}

module "ingestor_images_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_ingestor_images"
  topic_arns      = [module.image_inferrer_topic.arn]
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn

  visibility_timeout_seconds = local.image_ingestor_flush_interval_seconds + 60
}

# Service


module "ingestor_images" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_ingestor_images"
  container_image = local.ingestor_images_image
  security_group_ids = [
    # TODO: Do we need the egress security group?
    aws_security_group.service_egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  memory = 4096

  env_vars = {
    metrics_namespace = "${local.namespace_hyphen}_ingestor_images"
    ingest_queue_id   = module.ingestor_images_queue.url
    topic_arn         = module.image_ingestor_topic.arn

    es_images_index    = local.es_images_index
    es_augmented_index = local.es_images_augmented_index

    ingest_flush_interval_seconds = local.image_ingestor_flush_interval_seconds

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

  secret_env_vars = {
    es_host_catalogue     = "elasticsearch/catalogue/private_host"
    es_port_catalogue     = "catalogue/ingestor/es_port"
    es_username_catalogue = "catalogue/ingestor/es_username"
    es_password_catalogue = "catalogue/ingestor/es_password"
    es_protocol_catalogue = "catalogue/ingestor/es_protocol"

    es_host_pipeline_storage     = local.pipeline_storage_private_host
    es_port_pipeline_storage     = local.pipeline_storage_port
    es_protocol_pipeline_storage = local.pipeline_storage_protocol
    es_username_pipeline_storage = "elasticsearch/pipeline_storage_${var.pipeline_date}/image_ingestor/es_username"
    es_password_pipeline_storage = "elasticsearch/pipeline_storage_${var.pipeline_date}/image_ingestor/es_password"
  }

  use_fargate_spot = true

  subnets = var.subnets

  max_capacity = min(5, local.max_capacity)

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  queue_read_policy = module.ingestor_images_queue.read_policy

  deployment_service_env  = var.release_label
  deployment_service_name = "image-ingestor"

  depends_on = [
    null_resource.elasticsearch_users,
  ]

  shared_logging_secrets = var.shared_logging_secrets
}

module "image_ingestor_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_image_ingestor_output"
  role_names = [module.ingestor_images.task_role_name]
}

module "ingestor_images_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.ingestor_images_queue.name

  queue_high_actions = [module.ingestor_images.scale_up_arn]
  queue_low_actions  = [module.ingestor_images.scale_down_arn]
}
