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

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
