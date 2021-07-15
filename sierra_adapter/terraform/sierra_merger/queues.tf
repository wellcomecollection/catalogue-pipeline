module "input_queue" {
  source = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"

  queue_name = "${var.namespace}-sierra_${var.resource_type}_merger_input"

  topic_arns = [var.updates_topic_arn]

  # Ensure that messages are spread around -- if the merger has an error
  # (for example, hitting DynamoDB write limits), we don't retry too quickly.
  visibility_timeout_seconds = 300

  alarm_topic_arn = var.dlq_alarm_arn
}

module "scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.input_queue.name

  queue_high_actions = [module.service.scale_up_arn]
  queue_low_actions  = [module.service.scale_down_arn]
}
