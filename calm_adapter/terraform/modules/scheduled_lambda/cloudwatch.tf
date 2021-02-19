resource "aws_cloudwatch_event_rule" "scheduled_lambda_rule" {
  name                = "${var.name}_rule"
  description         = "Starts the ${var.name} lambda"
  schedule_expression = "rate(${var.schedule_interval})"
}

resource "aws_cloudwatch_event_target" "scheduled_lambda_event_target" {
  rule      = aws_cloudwatch_event_rule.scheduled_lambda_rule.name
  target_id = "${var.name}_target"
  arn       = aws_lambda_function.scheduled_lambda.arn
}

resource "aws_lambda_permission" "allow_cloudwatch_to_call_scheduled_lambda" {
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.scheduled_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.scheduled_lambda_rule.arn
}
