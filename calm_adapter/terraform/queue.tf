# TODO: Remove this once we're hooked into getting events for Calm
# This is used as a placeolder for where the
resource "aws_sns_topic" "test_receive_updates_topic" {
  name = "calm_adapter_test_receive_updates_topic"
}

module "ingestor_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace}_calm_adapter"
  topic_arns      = [aws_sns_topic.test_receive_updates_topic.arn]
  aws_region      = local.aws_region
  alarm_topic_arn = local.dlq_alarm_arn
}
