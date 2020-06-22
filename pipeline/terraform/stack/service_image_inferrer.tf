locals {
  inferrer_host = "localhost"
  inferrer_port = 80
  //  High inferrer throughput comes at the cost of the latency distribution
  // having heavy tails - this stops some unfortunate messages from being
  // put on the DLQ when they are consumed but not processed.
  queue_visibility_timeout = 60
}

module "image_inferrer_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name                 = "${local.namespace_hyphen}_image_inferrer"
  topic_arns                 = [module.image_id_minter_topic.arn]
  aws_region                 = var.aws_region
  alarm_topic_arn            = var.dlq_alarm_arn
  visibility_timeout_seconds = local.queue_visibility_timeout
}

module "image_inferrer" {
  source = "../modules/service_with_manager"

  service_name = "${local.namespace_hyphen}_image_inferrer"
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  host_cpu    = 4096
  host_memory = 8192

  manager_container_name  = "inference_manager"
  manager_container_image = local.inference_manager_image
  manager_cpu             = 512
  manager_memory          = 512

  app_container_name  = "inferrer"
  app_container_image = local.feature_inferrer_image
  app_cpu             = 3584
  app_memory          = 7680
  app_healthcheck = {
    command     = ["CMD-SHELL", "curl -f http://localhost:${local.inferrer_port}/healthcheck"],
    interval    = 30,
    retries     = 3,
    startPeriod = 30,
    timeout     = 5
  }

  manager_env_vars = {
    inferrer_host        = local.inferrer_host
    inferrer_port        = local.inferrer_port
    metrics_namespace    = "${local.namespace_hyphen}_image_inferrer"
    topic_arn            = module.image_inferrer_topic.arn
    messages_bucket_name = aws_s3_bucket.messages.id
    queue_url            = module.image_inferrer_queue.url
  }
  app_env_vars = {
    MODEL_OBJECT_KEY  = data.aws_ssm_parameter.inferrer_lsh_model_key.value
    MODEL_DATA_BUCKET = var.inferrer_model_data_bucket_name
  }

  subnets = var.subnets

  # Any higher than this currently causes latency spikes from Loris
  max_capacity = 8

  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.image_inferrer_queue.read_policy
}

resource "aws_iam_role_policy" "read_inferrer_data" {
  role   = module.image_inferrer.task_role_name
  policy = data.aws_iam_policy_document.allow_inferrer_data_access.json
}

data "aws_iam_policy_document" "allow_inferrer_data_access" {
  statement {
    actions = [
      "s3:GetObject*",
      "s3:ListBucket",
    ]

    resources = [
      "arn:aws:s3:::${var.inferrer_model_data_bucket_name}",
      "arn:aws:s3:::${var.inferrer_model_data_bucket_name}/*",
    ]
  }
}

module "image_inferrer_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_image_inferrer"
  role_names = [module.image_inferrer.task_role_name]

  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "image_inferrer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.image_inferrer_queue.name

  queue_high_actions = [module.image_inferrer.scale_up_arn]
  queue_low_actions  = [module.image_inferrer.scale_down_arn]
}
