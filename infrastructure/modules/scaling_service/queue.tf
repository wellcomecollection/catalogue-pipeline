module "input_queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.5.1"

  queue_name = var.queue_config.name

  topic_arns                 = var.queue_config.topic_arns
  visibility_timeout_seconds = var.queue_config.visibility_timeout_seconds
  max_receive_count          = var.queue_config.max_receive_count
  message_retention_seconds  = var.queue_config.message_retention_seconds
  alarm_topic_arn            = var.queue_config.dlq_alarm_arn

  main_q_age_alarm_action_arns = var.queue_config.main_q_age_alarm_action_arns
}

resource "aws_iam_role_policy" "read_from_q" {
  role   = module.task_definition.task_role_name
  policy = module.input_queue.read_policy
}

module "scaling_alarm" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.5.1"

  queue_name = module.input_queue.name

  queue_high_actions = [module.autoscaling.scale_up_arn]
  queue_low_actions  = [module.autoscaling.scale_down_arn]

  cooldown_period = var.queue_config.cooldown_period
}
