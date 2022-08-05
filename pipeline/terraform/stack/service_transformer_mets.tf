resource "aws_iam_role_policy" "mets_transformer_read_adapter_store" {
  role   = module.transformer_mets.task_role_name
  policy = var.adapter_config["mets"].read_policy
}

module "transformer_mets_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_transformer_mets_output"
  role_names = [module.transformer_mets.task_role_name]
}

module "transformer_mets" {
  source = "../modules/fargate_service"

  name            = "transformer_mets"
  container_image = local.transformer_mets_image

  topic_arns = local.mets_adapter_topic_arns

  # The default visibility timeout is 30 seconds, and occasionally we see
  # works get sent to the DLQ that still got through the transformer --
  # presumably because they took a bit too long to process.
  #
  # Bumping the timeout is an attempt to avoid the messages being
  # sent to a DLQ.
  queue_visibility_timeout_seconds = 90

  env_vars = {
    sns_topic_arn = module.transformer_mets_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["transformer"]

  # The METS transformer is quite CPU intensive, and if it doesn't have enough CPU,
  # the Akka scheduler gets resource-starved and the whole app stops doing anything.
  cpu    = 2048
  memory = 4096

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
