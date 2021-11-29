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

module "transformer_tei_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_transformer_tei_output"
  role_names = [module.transformer_tei.task_role_name]
}

module "transformer_tei" {
  source = "../modules/fargate_service"

  name            = "transformer_tei"
  container_image = local.transformer_tei_image

  topic_arns = local.tei_adapter_topic_arns

  # The default visibility timeout is 30 seconds, and occasionally we see
  # works get sent to the DLQ that still got through the transformer --
  # presumably because they took a bit too long to process.
  #
  # Bumping the timeout is an attempt to avoid the messages being
  # sent to a DLQ.
  queue_visibility_timeout_seconds = 90

  env_vars = {
    sns_topic_arn = module.transformer_tei_output_topic.arn

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
