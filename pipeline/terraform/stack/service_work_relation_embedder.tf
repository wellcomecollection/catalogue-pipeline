module "relation_embedder_input_queue" {
  source = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"

  queue_name                 = "${local.namespace_hyphen}_relation_embedder_input"
  topic_arns                 = [module.merger_works_topic.arn]
  visibility_timeout_seconds = 120

  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
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
    metrics_namespace    = "${local.namespace_hyphen}_relation_embedder"
    messages_bucket_name = aws_s3_bucket.messages.id

    queue_url = module.relation_embedder_input_queue.url
    topic_arn = module.relation_embedder_output_topic.arn

    es_merged_index       = local.es_works_merged_index
    es_denormalised_index = local.es_works_denormalised_index
  }

  secret_env_vars = {
    es_host     = "catalogue/pipeline_storage/es_host"
    es_port     = "catalogue/pipeline_storage/es_port"
    es_protocol = "catalogue/pipeline_storage/es_protocol"
    es_username = "catalogue/pipeline_storage/relation_embedder/es_username"
    es_password = "catalogue/pipeline_storage/relation_embedder/es_password"
  }

  subnets             = var.subnets
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.work_id_minter_queue.read_policy

  cpu    = 1024
  memory = 2048

  deployment_service_env  = var.release_label
  deployment_service_name = "work-relation-embedder"
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
  queue_name = module.relation_embedder_input_queue.name

  queue_high_actions = [module.relation_embedder.scale_up_arn]
  queue_low_actions  = [module.relation_embedder.scale_down_arn]
}
