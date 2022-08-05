resource "aws_iam_role_policy" "calm_transformer_read_adapter_store" {
  role   = module.transformer_calm.task_role_name
  policy = var.adapter_config["calm"].read_policy
}

module "transformer_calm_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_transformer_calm_output"
  role_names = [module.transformer_calm.task_role_name]
}

module "transformer_calm" {
  source = "../modules/fargate_service"

  name            = "transformer_calm"
  container_image = local.transformer_calm_image

  topic_arns = local.calm_adapter_topic_arns

  env_vars = {
    sns_topic_arn = module.transformer_calm_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["transformer"]

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
