module "reindexer_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "reindex_worker_queue"
  topic_arns      = [module.reindex_jobs_topic.arn]
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn

  # Messages take a while to process in the reindexer, so up from the default
  # queue timeout (30 seconds).
  visibility_timeout_seconds = 600
}

module "reindex_jobs_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "reindex_worker_jobs"
}
