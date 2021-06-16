module "tei_id_extractor_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name                 = "tei-windows"
  topic_arns                 = [module.tei_updater_lambda.topic_arn]
  aws_region                 = local.aws_region
  alarm_topic_arn            = local.dlq_alarm_arn
  visibility_timeout_seconds = 10800
}

module "tei_id_extractor" {
  source = "../../infrastructure/modules/worker"

  name = "tei_id_extractor"

  image = local.tei_id_extractor_image

  env_vars = {
    sqs_url   = module.tei_id_extractor_queue.url
    sns_topic = module.tei_id_extractor_topic.arn
  }
  secret_env_vars = {
  }

  min_capacity = 0
  max_capacity = 2

  cpu    = 512
  memory = 1024

  cluster_name             = aws_ecs_cluster.cluster.name
  cluster_arn              = aws_ecs_cluster.cluster.arn
  subnets                  = local.private_subnets
  shared_logging_secrets   = local.shared_logging_secrets
  elastic_cloud_vpce_sg_id = local.elastic_cloud_vpce_sg_id

  security_group_ids = [
    aws_security_group.egress.id,
  ]

  deployment_service_env  = local.release_label
  deployment_service_name = "tei-id-extractor"

  use_fargate_spot = true
}

resource "aws_iam_role_policy" "read_from_adapter_queue" {
  role   = module.tei_id_extractor.task_role_name
  policy = module.tei_id_extractor_queue.read_policy
}

resource "aws_iam_role_policy" "cloudwatch_push_metrics" {
  role   = module.tei_id_extractor.task_role_name
  policy = data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json
}

data "aws_iam_policy_document" "allow_cloudwatch_push_metrics" {
  statement {
    actions = [
      "cloudwatch:PutMetricData",
    ]

    resources = [
      "*",
    ]
  }
}

module "adapter_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = module.tei_id_extractor_queue.name

  queue_high_actions = [
    module.tei_id_extractor.scale_up_arn
  ]

  queue_low_actions = [
    module.tei_id_extractor.scale_down_arn
  ]
}
