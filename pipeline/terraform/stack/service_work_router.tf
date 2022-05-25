module "router_path_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_router_path_output"
  role_names = [module.router.task_role_name]
}

module "router_candidate_incomplete_paths_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_router_candidate_incomplete_paths_output"
  role_names = [module.router.task_role_name]
}

module "router_work_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_router_work_output"
  role_names = [module.router.task_role_name]
}

module "router" {
  source = "../modules/fargate_service"

  name            = "router"
  container_image = local.router_image

  topic_arns = [
    module.merger_works_output_topic.arn,
  ]

  env_vars = {
    queue_parallelism = 10

    paths_topic_arn             = module.router_path_output_topic.arn
    path_concatenator_topic_arn = module.router_candidate_incomplete_paths_output_topic.arn
    works_topic_arn             = module.router_work_output_topic.arn

    es_merged_index       = local.es_works_merged_index
    es_denormalised_index = local.es_works_denormalised_index
    batch_size            = 100
    # The flush interval must be sufficiently lower than the message timeout
    # to allow the messages to be processed after the flush inteval but before
    # they expire.  The upstream queue timeout is not set by us, leaving it
    # at the default 30 seconds.
    # See https://github.com/wellcomecollection/platform/issues/5463
    flush_interval_seconds = 20
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["router"]

  cpu    = 1024
  memory = 2048

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  # Below this line is boilerplate that should be the same across
  # all Fargate services.
  egress_security_group_id             = aws_security_group.egress.id
  elastic_cloud_vpce_security_group_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.id

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  dlq_alarm_topic_arn = var.dlq_alarm_arn

  subnets = var.subnets

  namespace = local.namespace

  deployment_service_env = var.release_label

  shared_logging_secrets = var.shared_logging_secrets
}
