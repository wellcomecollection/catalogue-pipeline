module "calm_windows_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name                 = "calm-windows"
  topic_arns                 = [aws_sns_topic.calm_windows_topic.arn]
  aws_region                 = local.aws_region
  alarm_topic_arn            = local.dlq_alarm_arn
  visibility_timeout_seconds = 10800
}

resource "aws_iam_role_policy" "read_from_queue" {
  role   = module.worker.task_role_name
  policy = module.calm_windows_queue.read_policy
}

module "scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = module.calm_windows_queue.name

  queue_high_actions = [
    module.worker.scale_up_arn
  ]

  queue_low_actions  = [
    module.worker.scale_down_arn
  ]
}
