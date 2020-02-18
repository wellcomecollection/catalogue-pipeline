# Topics

module "snapshot_generator_jobs_topic" {
  source = "../modules/topic"
  name   = "snapshot_generator_jobs"
}

module "snapshot_complete_topic" {
  source = "../modules/topic"
  name   = "snapshot_generation_complete"
}

module "snapshot_alarm_topic" {
  source = "../modules/topic"
  name   = "snapshot_alarm"
}

# Queues
module "snapshot_generator_queue" {
  source = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name = "snapshot_generator_queue"
  topic_arns = [module.snapshot_scheduler.topic_arn]
  aws_region = var.aws_region
  alarm_topic_arn = local.dlq_alarm_arn
  visibility_timeout_seconds = 1800
}

# We'll get alarms from the snapshot generator DLQ
# it's also a problem if the main queue starts backfilling
# it means snapshots aren't being created correctly.
resource "aws_cloudwatch_metric_alarm" "snapshot_scheduler_queue_not_empty" {
  alarm_name          = "snapshot_scheduler_queue_not_empty"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 3600
  threshold           = 2
  statistic           = "Average"

  dimensions {
    QueueName = module.snapshot_generator_queue.name
  }

  alarm_actions = [module.snapshot_alarm_topic.arn]
}
