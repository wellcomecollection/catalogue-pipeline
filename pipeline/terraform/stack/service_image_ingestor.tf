module "ingestor_images_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_ingestor_images"
  topic_arns      = [module.image_inferrer_topic.arn]
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

# Service


module "ingestor_images" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_ingestor_images"
  container_image = local.ingestor_images_image
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  memory = 4096

  env_vars = {
    metrics_namespace = "${local.namespace_hyphen}_ingestor_images"
    ingest_queue_id   = module.ingestor_images_queue.url

    es_images_index    = local.es_images_index
    es_augmented_index = local.es_images_augmented_index

    ingest_batch_size             = 100
    ingest_flush_interval_seconds = 60
  }

  secret_env_vars = {
    es_host_catalogue     = "catalogue/ingestor/es_host"
    es_port_catalogue     = "catalogue/ingestor/es_port"
    es_username_catalogue = "catalogue/ingestor/es_username"
    es_password_catalogue = "catalogue/ingestor/es_password"
    es_protocol_catalogue = "catalogue/ingestor/es_protocol"

    es_host_pipeline_storage     = local.pipeline_storage_es_host
    es_port_pipeline_storage     = local.pipeline_storage_es_port
    es_protocol_pipeline_storage = local.pipeline_storage_es_protocol
    es_username_pipeline_storage = "catalogue/${var.pipeline_storage_id}/image_ingestor/es_username"
    es_password_pipeline_storage = "catalogue/${var.pipeline_storage_id}/image_ingestor/es_password"
  }


  subnets = var.subnets

  max_capacity        = min(5, var.max_capacity)
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.ingestor_images_queue.read_policy

  deployment_service_env  = var.release_label
  deployment_service_name = "image-ingestor"

  shared_logging_secrets = var.shared_logging_secrets
}

module "ingestor_images_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.ingestor_images_queue.name

  queue_high_actions = [module.ingestor_images.scale_up_arn]
  queue_low_actions  = [module.ingestor_images.scale_down_arn]
}
