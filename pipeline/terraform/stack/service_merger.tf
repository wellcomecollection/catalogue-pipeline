module "merger_works_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_merger_works_output"
  role_names = [module.merger.task_role_name]
}

module "merger_images_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_merger_images_output"
  role_names = [module.merger.task_role_name]
}

module "merger" {
  source = "../modules/fargate_service"

  name            = "merger"
  container_image = local.merger_image

  topic_arns = [
    module.matcher_output_topic.arn,
  ]

  # This has to be longer than the `flush_interval_seconds` in the merger.
  # It also has to be long enough for the Work to actually get processed,
  # and some of them are quite big.
  queue_visibility_timeout_seconds = 20 * 60

  env_vars = {
    merger_works_topic_arn  = module.merger_works_output_topic.arn
    merger_images_topic_arn = module.merger_images_output_topic.arn

    es_identified_works_index = local.es_works_identified_index
    es_merged_works_index     = local.es_works_merged_index
    es_initial_images_index   = local.es_images_initial_index

    batch_size             = 50
    flush_interval_seconds = 120
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["merger"]

  cpu    = 2048
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
