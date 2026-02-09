locals {
  create_queue = var.queue_config != null
}

module "input_queue" {
  count  = local.create_queue ? 1 : 0
  source = "github.com/wellcomecollection/terraform-aws-sqs.git//queue?ref=v1.5.1"

  queue_name = local.queue_name

  topic_arns                 = var.queue_config.topic_arns
  visibility_timeout_seconds = var.queue_config.visibility_timeout_seconds
  max_receive_count          = var.queue_config.max_receive_count
  message_retention_seconds  = var.queue_config.message_retention_seconds
  alarm_topic_arn            = var.queue_config.dlq_alarm_arn
}

resource "aws_iam_role_policy_attachment" "lambda_sqs_role_policy" {
  count      = local.create_queue ? 1 : 0
  role       = module.pipeline_step.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaSQSQueueExecutionRole"
}

resource "aws_lambda_event_source_mapping" "event_source_mapping" {
  count = local.create_queue ? 1 : 0

  event_source_arn = module.input_queue[0].arn
  enabled          = var.event_source_enabled
  function_name    = module.pipeline_step.lambda.function_name

  # Scaling configuration
  # See: https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-scaling.html
  scaling_config {
    maximum_concurrency = var.queue_config.maximum_concurrency
  }

  # Batching configuration
  # See: https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html#sqs-polling-behavior
  batch_size                         = var.queue_config.batch_size
  maximum_batching_window_in_seconds = var.queue_config.batching_window_seconds
}
