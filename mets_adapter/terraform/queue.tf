module "queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs.git//queue?ref=v1.1.2"

  queue_name = "mets_adapter_queue"

  topic_arns = [
    local.storage_notifications_topic_arn,
  ]

  aws_region = local.aws_region

  alarm_topic_arn = local.dlq_alarm_arn
}

module "scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = module.queue.name

  queue_high_actions = [
    module.worker.scale_up_arn
  ]

  queue_low_actions = [
    module.worker.scale_down_arn
  ]
}
