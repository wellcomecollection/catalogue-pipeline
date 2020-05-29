module "demultiplexer_queue" {
  source = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"

  queue_name = "${var.namespace}-sierra_demultiplexed_items"
  topic_arns = [var.demultiplexer_topic_arn]

  max_receive_count = 10

  alarm_topic_arn = var.dlq_alarm_arn

  aws_region = var.aws_region
}

module "scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = "sierra_demultiplexed_items"

  queue_high_actions = [module.sierra_to_dynamo_service.scale_up_arn]
  queue_low_actions  = [module.sierra_to_dynamo_service.scale_down_arn]
}
