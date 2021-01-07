module "relation_embedder_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name                 = "${local.namespace_hyphen}_relation_embedder"
  topic_arns                 = [module.batcher_output_topic.arn]
  visibility_timeout_seconds = 600
  aws_region                 = var.aws_region
  alarm_topic_arn            = var.dlq_alarm_arn
}

module "relation_embedder" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_relation_embedder"
  container_image = local.relation_embedder_image

  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.id

  env_vars = {
    metrics_namespace = "${local.namespace_hyphen}_relation_embedder"

    queue_url = module.relation_embedder_queue.url
    topic_arn = module.relation_embedder_output_topic.arn

    es_merged_index       = local.es_works_merged_index
    es_denormalised_index = local.es_works_denormalised_index

    queue_parallelism            = 3  // NOTE: limit to avoid memory errors
    affected_works_scroll_size   = 50 // NOTE: limit to avoid memory errors
    complete_tree_scroll_size    = 800
    index_batch_size             = 100 // NOTE: too large results in 413 from ES
    index_flush_interval_seconds = 60
  }

  secret_env_vars = {
    es_host     = "catalogue/pipeline_storage/es_host"
    es_port     = "catalogue/pipeline_storage/es_port"
    es_protocol = "catalogue/pipeline_storage/es_protocol"
    es_username = "catalogue/pipeline_storage/relation_embedder/es_username"
    es_password = "catalogue/pipeline_storage/relation_embedder/es_password"
  }

  # NOTE: limit to avoid >500 concurrent scroll contexts
  max_capacity        = min(10, var.max_capacity)

  subnets             = var.subnets
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.relation_embedder_queue.read_policy

  cpu    = 1024
  memory = 2048

  deployment_service_env  = var.release_label
  deployment_service_name = "work-relation-embedder"
  shared_logging_secrets  = var.shared_logging_secrets
}

# Output topic

module "relation_embedder_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_relation_embedder_output"
  role_names = [module.relation_embedder.task_role_name]

  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "relation_embedder_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.relation_embedder_queue.name

  queue_high_actions = [module.relation_embedder.scale_up_arn]
  queue_low_actions  = [module.relation_embedder.scale_down_arn]
}
