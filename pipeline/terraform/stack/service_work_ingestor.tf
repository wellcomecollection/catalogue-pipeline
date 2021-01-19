module "ingestor_works_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_ingestor_works"
  topic_arns      = [module.router_work_output_topic.arn, module.relation_embedder_output_topic.arn]
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn

  max_receive_count = 6
}

# Service

module "ingestor_works" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_ingestor_works"
  container_image = local.ingestor_works_image
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  env_vars = {
    metrics_namespace             = "${local.namespace_hyphen}_ingestor_works"
    es_works_index                = local.es_works_index
    es_denormalised_index         = local.es_works_denormalised_index
    ingest_queue_id               = module.ingestor_works_queue.url
    topic_arn                     = module.work_ingestor_topic.arn
    ingest_batch_size             = 100
    ingest_flush_interval_seconds = 60
  }

  secret_env_vars = merge({
    es_host_catalogue     = "catalogue/ingestor/es_host"
    es_port_catalogue     = "catalogue/ingestor/es_port"
    es_username_catalogue = "catalogue/ingestor/es_username"
    es_password_catalogue = "catalogue/ingestor/es_password"
    es_protocol_catalogue = "catalogue/ingestor/es_protocol"

    es_host_pipeline_storage     = local.pipeline_storage_es_host
    es_port_pipeline_storage     = local.pipeline_storage_es_port
    es_protocol_pipeline_storage = local.pipeline_storage_es_protocol
    es_username_pipeline_storage = "catalogue/${var.pipeline_storage_id}/work_ingestor/es_username"
    es_password_pipeline_storage = "catalogue/${var.pipeline_storage_id}/work_ingestor/es_password"
  })

  subnets = var.subnets

  max_capacity        = min(6, var.max_capacity)
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.ingestor_works_queue.read_policy

  cpu    = 1024
  memory = 2048

  deployment_service_env  = var.release_label
  deployment_service_name = "work-ingestor"
  shared_logging_secrets  = var.shared_logging_secrets
}

# Output topic

module "work_ingestor_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_work_ingestor"
  role_names = [module.ingestor_works.task_role_name]

  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "ingestor_works_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.ingestor_works_queue.name

  queue_high_actions = [module.ingestor_works.scale_up_arn]
  queue_low_actions  = [module.ingestor_works.scale_down_arn]
}
