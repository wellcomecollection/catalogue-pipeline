module "input_queue" {
  source = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"

  queue_name = "${local.service_name}_input"
  topic_arns = [var.demultiplexer_topic_arn]

  max_receive_count = 10

  alarm_topic_arn = var.dlq_alarm_arn
}

module "scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.input_queue.name

  queue_high_actions = [module.service.scale_up_arn]
  queue_low_actions  = [module.service.scale_down_arn]
}
