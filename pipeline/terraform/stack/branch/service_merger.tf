module "merger_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name      = "${local.namespace}_merger"
  topic_arns      = [var.matcher_topic_arn]
  alarm_topic_arn = var.dlq_alarm_arn

  # This has to be longer than the `flush_interval_seconds` in the merger.
  # It also has to be long enough for the Work to actually get processed,
  # and some of them are quite big.
  visibility_timeout_seconds = 20 * 60
}
module "merger" {
  source          = "../../modules/service"
  service_name    = "${local.namespace}_merger"
  container_image = var.merger_image
  security_group_ids = [
    var.service_egress_security_group_id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = var.cluster_name
  cluster_arn  = data.aws_ecs_cluster.cluster.arn

  env_vars = {
    metrics_namespace       = "${local.namespace}_merger"
    merger_queue_id         = module.merger_queue.url
    merger_works_topic_arn  = module.merger_works_topic.arn
    merger_images_topic_arn = module.merger_images_topic.arn

    es_identified_works_index = var.es_works_identified_index
    es_merged_works_index     = local.es_works_merged_index
    es_initial_images_index   = local.es_images_initial_index

    batch_size             = 50
    flush_interval_seconds = 120

    toggle_tei_on = var.toggle_tei_on
  }

  secret_env_vars = var.pipeline_storage_es_service_secrets["merger"]

  cpu    = 2048
  memory = 4096

  use_fargate_spot = true

  subnets = var.subnets

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment

  queue_read_policy = module.merger_queue.read_policy

  depends_on = [
    var.elasticsearch_users,
  ]

  deployment_service_env  = var.release_label
  deployment_service_name = "merger-${local.tei_suffix}"
  shared_logging_secrets  = var.shared_logging_secrets
}

module "merger_works_topic" {
  source = "../../modules/topic"

  name       = "${local.namespace}_merger_works"
  role_names = [module.merger.task_role_name]
}

module "merger_images_topic" {
  source = "../../modules/topic"

  name       = "${local.namespace}_merger_images"
  role_names = [module.merger.task_role_name]
}

module "merger_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.merger_queue.name

  queue_high_actions = [module.merger.scale_up_arn]
  queue_low_actions  = [module.merger.scale_down_arn]
}
