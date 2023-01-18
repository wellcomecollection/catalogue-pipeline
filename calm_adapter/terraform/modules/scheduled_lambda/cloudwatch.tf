resource "aws_cloudwatch_event_rule" "scheduled_lambda_rule" {
  name                = "${var.name}_rule"
  description         = "Starts the ${var.name} lambda"
  schedule_expression = "rate(${var.schedule_interval})"
  is_enabled          = var.events_enabled
}

resource "aws_cloudwatch_event_target" "scheduled_lambda_event_target" {
  rule      = aws_cloudwatch_event_rule.scheduled_lambda_rule.name
  target_id = "${var.name}_target"
  arn       = module.scheduled_lambda.lambda.arn
}

resource "aws_lambda_permission" "allow_cloudwatch_to_call_scheduled_lambda" {
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = module.scheduled_lambda.lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.scheduled_lambda_rule.arn
}
