module "miro_transformer_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_miro_transformer"
  topic_arns      = var.miro_adapter_topic_arns
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

module "miro_transformer" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_miro_transformer"
  container_image = local.transformer_miro_image
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  cpu    = 1024
  memory = 2048

  env_vars = {
    transformer_queue_id = module.miro_transformer_queue.url
    metrics_namespace    = "${local.namespace_hyphen}_miro_transformer"

    sns_topic_arn = module.miro_transformer_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = {
    es_host     = var.pipeline_storage_es_host_secret_id
    es_port     = "catalogue/pipeline_storage/es_port"
    es_protocol = "catalogue/pipeline_storage/es_protocol"
    es_username = "catalogue/pipeline_storage/transformer/es_username"
    es_password = "catalogue/pipeline_storage/transformer/es_password"
  }

  subnets             = var.subnets
  max_capacity        = var.max_capacity
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.miro_transformer_queue.read_policy

  deployment_service_env  = var.release_label
  deployment_service_name = "miro-transformer"
  shared_logging_secrets  = var.shared_logging_secrets
}

resource "aws_iam_role_policy" "miro_transformer_vhs_miro_adapter_read" {
  role   = module.miro_transformer.task_role_name
  policy = var.vhs_miro_read_policy
}

module "miro_transformer_output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic?ref=v1.0.1"

  name = "${local.namespace_hyphen}_miro_transformer_output"
}

resource "aws_iam_role_policy" "allow_miro_transformer_sns_publish" {
  role   = module.miro_transformer.task_role_name
  policy = module.miro_transformer_output_topic.publish_policy
}

module "miro_transformer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.miro_transformer_queue.name

  queue_high_actions = [module.miro_transformer.scale_up_arn]
  queue_low_actions  = [module.miro_transformer.scale_down_arn]
}
