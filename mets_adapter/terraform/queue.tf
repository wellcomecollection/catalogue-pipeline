module "queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs.git//queue?ref=v1.1.2"

  queue_name = "mets_adapter_queue"

  topic_arns = [
    local.storage_notifications_topic_arn,
  ]

  aws_region = local.aws_region

  alarm_topic_arn = local.dlq_alarm_arn
}