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

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id

  env_vars = {
    metrics_namespace   = "${local.namespace_hyphen}_ingestor_images"
    es_index            = var.es_images_index
    ingest_queue_id     = module.ingestor_images_queue.url
    es_ingest_batchSize = 100
  }

  secret_env_vars = {
    es_host     = "catalogue/ingestor/es_host"
    es_port     = "catalogue/ingestor/es_port"
    es_username = "catalogue/ingestor/es_username"
    es_password = "catalogue/ingestor/es_password"
    es_protocol = "catalogue/ingestor/es_protocol"
  }


  subnets    = var.subnets
  aws_region = var.aws_region

  max_capacity        = 10
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.ingestor_images_queue.read_policy
}

module "ingestor_images_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.ingestor_images_queue.name

  queue_high_actions = [module.ingestor_images.scale_up_arn]
  queue_low_actions  = [module.ingestor_images.scale_down_arn]
}
