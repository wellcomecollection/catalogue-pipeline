module "window_generator_lambda" {
  source = "../lambda"

  name = "tei_window_generator"

  s3_bucket   = var.infra_bucket
  s3_key      = "lambdas/common/window_generator.zip"
  module_name = "window_generator"

  description     = "Send windows to the TEI adapter"
  alarm_topic_arn = var.lambda_error_alarm_arn
  timeout         = 10

  environment_variables = {
    "TOPIC_ARN"             = module.windows_topic.arn
    "WINDOW_LENGTH_MINUTES" = var.window_length_minutes
  }

  log_retention_in_days = 30
}

resource "random_id" "cloudwatch_trigger_name" {
  byte_length = 8
  prefix      = "AllowExecutionFromCloudWatch_${module.window_generator_lambda.function_name}_"
}

resource "aws_lambda_permission" "allow_cloudwatch_trigger" {
  statement_id  = random_id.cloudwatch_trigger_name.id
  action        = "lambda:InvokeFunction"
  function_name = module.window_generator_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.window_generator_rule.arn
}

resource "aws_cloudwatch_event_target" "event_trigger_custom" {
  rule  = aws_cloudwatch_event_rule.window_generator_rule.id
  arn   = module.window_generator_lambda.arn
  input = "{}"
}
