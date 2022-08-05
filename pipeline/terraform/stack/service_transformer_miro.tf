resource "aws_iam_role_policy" "miro_transformer_read_adapter_store" {
  role   = module.transformer_miro.task_role_name
  policy = var.adapter_config["miro"].read_policy
}

module "transformer_miro_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_transformer_miro_output"
  role_names = [module.transformer_miro.task_role_name]
}

module "transformer_miro" {
  source = "../modules/fargate_service"

  name            = "transformer_miro"
  container_image = local.transformer_miro_image

  topic_arns = local.miro_adapter_topic_arns

  env_vars = {
    sns_topic_arn = module.transformer_miro_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["transformer"]

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

  shared_logging_secrets = var.logging_config.shared_secrets
}
