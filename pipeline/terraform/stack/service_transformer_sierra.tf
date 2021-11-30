resource "aws_iam_role_policy" "sierra_transformer_vhs_sierra_adapter_read" {
  role   = module.transformer_sierra.task_role_name
  policy = var.vhs_sierra_read_policy
}

module "transformer_sierra_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_transformer_sierra_output"
  role_names = [module.transformer_sierra.task_role_name]
}

module "transformer_sierra" {
  source = "../modules/fargate_service"

  name            = "transformer_sierra"
  container_image = local.transformer_sierra_image

  topic_arns = local.sierra_adapter_topic_arns

  env_vars = {
    sns_topic_arn = module.transformer_sierra_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["transformer"]

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
