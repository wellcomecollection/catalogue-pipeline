module "input_queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"

  queue_name = "${var.namespace}_${var.name}_input"

  topic_arns                 = var.topic_arns
  visibility_timeout_seconds = var.queue_visibility_timeout_seconds

  alarm_topic_arn = var.dlq_alarm_topic_arn
}

resource "aws_iam_role_policy" "read_from_q" {
  role   = module.worker.task_role_name
  policy = module.input_queue.read_policy
}

output "queue_url" {
  value = module.input_queue.url
}

module "scaling_alarm" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"

  queue_name = module.input_queue.name

  queue_high_actions = [module.worker.scale_up_arn]
  queue_low_actions  = [module.worker.scale_down_arn]
}
