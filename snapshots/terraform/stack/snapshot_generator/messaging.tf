module "snapshot_generator_input_queue" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name = "snapshot_generator-${var.deployment_service_env}_input"
  topic_arns = [var.snapshot_generator_input_topic_arn]

  # This should be longer than the time we expect a snapshot to take
  visibility_timeout_seconds = 60 * 30

  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

module "snapshot_generator_output_topic" {
  source = "git::github.com/wellcomecollection/terraform-aws-sns-topic?ref=v1.0.1"

  name = "snapshot_complete-${var.deployment_service_env}"
}

module "completed_snapshots_queue" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name = "completed_snapshots-${var.deployment_service_env}"
  topic_arns = [module.snapshot_generator_output_topic.arn]

  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

resource "aws_iam_role_policy" "allow_generator_to_publish_to_topic" {
  role   = module.snapshot_generator.task_role_name
  policy = module.snapshot_generator_output_topic.publish_policy
}
