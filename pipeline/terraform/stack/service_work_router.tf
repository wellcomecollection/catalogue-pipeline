module "router_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_router"
  topic_arns      = [module.merger_works_topic.arn]
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

module "router" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_router"
  container_image = local.router_image

  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.id

  env_vars = {
    metrics_namespace = "${local.namespace_hyphen}_router"

    queue_url = module.router_queue.url

    paths_topic_arn = module.router_path_output_topic.arn
    works_topic_arn = module.router_work_output_topic.arn

    es_merged_index       = local.es_works_merged_index
    es_denormalised_index = local.es_works_denormalised_index
  }

  secret_env_vars = {
    es_host     = "catalogue/pipeline_storage/es_host"
    es_port     = "catalogue/pipeline_storage/es_port"
    es_protocol = "catalogue/pipeline_storage/es_protocol"
    es_username = "catalogue/pipeline_storage/router/es_username"
    es_password = "catalogue/pipeline_storage/router/es_password"
  }

  subnets             = var.subnets
  max_capacity        = 5
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.router_queue.read_policy

  cpu    = 1024
  memory = 2048

  deployment_service_env  = var.release_label
  deployment_service_name = "work-relation-embedder"
}

module "router_path_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_router_path_output"
  role_names = [module.router.task_role_name]

  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "router_work_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_router_work_output"
  role_names = [module.router.task_role_name]

  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "router_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.router_queue.name

  queue_high_actions = [module.router.scale_up_arn]
  queue_low_actions  = [module.router.scale_down_arn]
}
