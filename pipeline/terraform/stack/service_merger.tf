module "merger_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name      = "${local.namespace}_merger"
  topic_arns      = [module.matcher_topic.arn]
  alarm_topic_arn = var.dlq_alarm_arn

  # This has to be longer than the `flush_interval_seconds` in the merger.
  # It also has to be long enough for the Work to actually get processed,
  # and some of them are quite big.
  visibility_timeout_seconds = 20 * 60
}
module "merger" {
  source = "../modules/service"

  namespace = local.namespace
  name      = "merger"

  container_image = local.merger_image
  security_group_ids = [
    aws_security_group.service_egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = data.aws_ecs_cluster.cluster.arn

  env_vars = {
    metrics_namespace       = "merger"
    merger_queue_id         = module.merger_queue.url
    merger_works_topic_arn  = module.merger_works_topic.arn
    merger_images_topic_arn = module.merger_images_topic.arn

    es_identified_works_index = local.es_works_identified_index
    es_merged_works_index     = local.es_works_merged_index
    es_initial_images_index   = local.es_images_initial_index

    batch_size             = 50
    flush_interval_seconds = 120

    toggle_tei_on = true
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["merger"]

  cpu    = 2048
  memory = 4096

  use_fargate_spot = true

  subnets = var.subnets

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  queue_read_policy = module.merger_queue.read_policy

  deployment_service_env  = var.release_label
  deployment_service_name = "merger"
  shared_logging_secrets  = var.shared_logging_secrets
}

module "merger_works_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_merger_works"
  role_names = [module.merger.task_role_name]
}

module "merger_images_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_merger_images"
  role_names = [module.merger.task_role_name]
}

module "merger_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.merger_queue.name

  queue_high_actions = [module.merger.scale_up_arn]
  queue_low_actions  = [module.merger.scale_down_arn]
}
