module "mets_transformer_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_mets_transformer"
  topic_arns      = var.mets_adapter_topic_arns
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

module "mets_transformer" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_mets_transformer"
  container_image = local.transformer_mets_image
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  env_vars = {
    transformer_queue_id = module.mets_transformer_queue.url
    metrics_namespace    = "${local.namespace_hyphen}_mets_transformer"

    mets_adapter_dynamo_table_name = var.mets_adapter_table_name

    sns_topic_arn = module.mets_transformer_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = {
    es_host     = "catalogue/pipeline_storage/es_host"
    es_port     = "catalogue/pipeline_storage/es_port"
    es_protocol = "catalogue/pipeline_storage/es_protocol"
    es_username = "catalogue/pipeline_storage/transformer/es_username"
    es_password = "catalogue/pipeline_storage/transformer/es_password"
  }

  subnets             = var.subnets
  max_capacity        = 10
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.mets_transformer_queue.read_policy

  cpu    = 1024
  memory = 2048

  deployment_service_env  = var.release_label
  deployment_service_name = "mets-transformer"
  shared_logging_secrets  = var.shared_logging_secrets
}

module "mets_transformer_output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic?ref=v1.0.1"

  name = "${local.namespace_hyphen}_mets_transformer_output"
}

resource "aws_iam_role_policy" "allow_mets_transformer_sns_publish" {
  role   = module.mets_transformer.task_role_name
  policy = module.mets_transformer_output_topic.publish_policy
}

module "mets_transformer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.mets_transformer_queue.name

  queue_high_actions = [module.mets_transformer.scale_up_arn]
  queue_low_actions  = [module.mets_transformer.scale_down_arn]
}

resource "aws_iam_role_policy" "read_mets_adapter_table" {
  role   = module.mets_transformer.task_role_name
  policy = var.mets_adapter_read_policy
}

data "aws_iam_policy_document" "read_storage_bucket" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject*",
    ]

    resources = [
      "arn:aws:s3:::${var.storage_bucket_name}",
      "arn:aws:s3:::${var.storage_bucket_name}/*",
    ]
  }
}

resource "aws_iam_role_policy" "read_storage_bucket" {
  role   = module.mets_transformer.task_role_name
  policy = data.aws_iam_policy_document.read_storage_bucket.json
}
