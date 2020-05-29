module "windows_queue" {
  source = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"

  queue_name = "${var.namespace}-sierra_${var.resource_type}_windows"
  topic_arns = var.windows_topic_arns

  # Ensure that messages are spread around -- if we get a timeout from the
  # Sierra API, we don't retry _too_ quickly.
  visibility_timeout_seconds = 300

  # In certain periods of high activity, we've seen the Sierra API timeout
  # multiple times.  Since the reader can restart a partially-completed
  # window, it's okay to retry the window several times.
  max_receive_count = 12

  alarm_topic_arn = var.dlq_alarm_arn

  aws_region = var.aws_region
}

module "scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = "sierra_${var.resource_type}_windows"

  queue_high_actions = [module.sierra_reader_service.scale_up_arn]
  queue_low_actions  = [module.sierra_reader_service.scale_down_arn]
}
