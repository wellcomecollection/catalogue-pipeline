locals {
  queue_name = var.queue_name == null ? trim("${local.namespace}_${var.name}_input", "_") : var.queue_name
}

module "input_queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"

  queue_name = local.queue_name

  topic_arns                 = var.topic_arns
  visibility_timeout_seconds = var.queue_visibility_timeout_seconds
  max_receive_count          = var.max_receive_count
  message_retention_seconds  = var.message_retention_seconds
  alarm_topic_arn            = var.fargate_service_boilerplate.dlq_alarm_topic_arn
}

resource "aws_iam_role_policy" "read_from_q" {
  role   = module.scaling_service.task_role_name
  policy = module.input_queue.read_policy
}

module "scaling_alarm" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.3.0"

  queue_name = module.input_queue.name

  queue_high_actions = [module.scaling_service.scale_up_arn]
  queue_low_actions  = [module.scaling_service.scale_down_arn]

  cooldown_period = var.cooldown_period
}
