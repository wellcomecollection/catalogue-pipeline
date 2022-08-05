
module "path_concatenator_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_path_concatenator_output_topic"
  role_names = [module.path_concatenator.task_role_name]
}

module "path_concatenator" {
  source = "../modules/fargate_service"

  name            = "path_concatenator"
  container_image = local.path_concatenator_image

  topic_arns = [
    module.router_candidate_incomplete_paths_output_topic.arn,
  ]

  env_vars = {
    queue_parallelism = 10

    downstream_topic_arn = module.path_concatenator_output_topic.arn

    es_merged_index = local.es_works_merged_index
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["path_concatenator"]

  cpu    = 1024
  memory = 2048

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

  shared_logging_secrets = var.shared_logging_secrets
}
