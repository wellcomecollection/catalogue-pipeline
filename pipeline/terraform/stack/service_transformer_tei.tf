module "transformer_tei_input_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name      = "${local.namespace}_transformer_tei_input"
  topic_arns      = local.tei_adapter_topic_arns
  alarm_topic_arn = var.dlq_alarm_arn

  # The default visibility timeout is 30 seconds, and occasionally we see
  # works get sent to the DLQ that still got through the transformer --
  # presumably because they took a bit too long to process.
  #
  # Bumping the timeout is an attempt to avoid the messages being
  # sent to a DLQ.
  visibility_timeout_seconds = 90
}

module "transformer_tei" {
  source = "../modules/service"

  namespace = local.namespace
  name      = "transformer_tei"

  container_image = local.transformer_tei_image
  security_group_ids = [
    aws_security_group.egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  env_vars = {
    transformer_queue_id = module.transformer_tei_input_queue.url
    metrics_namespace    = "${local.namespace_hyphen}_tei_transformer"

    sns_topic_arn = module.transformer_tei_output_topic.arn

    es_index = local.es_works_source_index

    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["transformer"]

  subnets = var.subnets

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  queue_read_policy = module.transformer_tei_input_queue.read_policy

  # The METS transformer is quite CPU intensive, and if it doesn't have enough CPU,
  # the Akka scheduler gets resource-starved and the whole app stops doing anything.
  cpu    = 2048
  memory = 4096

  use_fargate_spot = true

  deployment_service_env  = var.release_label
  deployment_service_name = "tei-transformer"
  shared_logging_secrets  = var.shared_logging_secrets
}

module "transformer_tei_output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic?ref=v1.0.1"

  name = "${local.namespace}_transformer_tei_output"
}

resource "aws_iam_role_policy" "allow_tei_transformer_sns_publish" {
  role   = module.transformer_tei.task_role_name
  policy = module.transformer_tei_output_topic.publish_policy
}

module "tei_transformer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.transformer_tei_input_queue.name

  queue_high_actions = [module.transformer_tei.scale_up_arn]
  queue_low_actions  = [module.transformer_tei.scale_down_arn]
}

data "aws_iam_policy_document" "read_tei_adapter_bucket" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject*",
    ]

    resources = [
      "arn:aws:s3:::${var.tei_adapter_bucket_name}",
      "arn:aws:s3:::${var.tei_adapter_bucket_name}/*",
    ]
  }
}

resource "aws_iam_role_policy" "read_tei_adapter_bucket" {
  role   = module.transformer_tei.task_role_name
  policy = data.aws_iam_policy_document.read_tei_adapter_bucket.json
}
