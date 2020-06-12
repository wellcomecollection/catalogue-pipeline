module "updates_queue" {
  source = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"

  queue_name = "${var.namespace}-sierra_items_merger_queue"

  topic_arns = [
    var.updates_topic_arn
  ]

  # Ensure that messages are spread around -- if the merger has an error
  # (for example, hitting DynamoDB write limits), we don't retry too quickly.
  visibility_timeout_seconds = 300

  # The bib merger queue has had consistent problems where the DLQ fills up,
  # and then redriving it fixes everything.  Increase the number of times a
  # message can be received before it gets marked as failed.
  max_receive_count = 12

  alarm_topic_arn = var.dlq_alarm_arn

  aws_region = var.aws_region
}

module "scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = module.updates_queue.name

  queue_high_actions = [module.sierra_merger_service.scale_up_arn]
  queue_low_actions  = [module.sierra_merger_service.scale_down_arn]
}
