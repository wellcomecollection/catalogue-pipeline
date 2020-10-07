resource "aws_cloudwatch_event_rule" "snapshot_reporter_rule" {
  name                = "snapshot_reporter_rule-${var.deployment_service_env}"
  description         = "Starts the snapshot_reporter (${var.deployment_service_env}) lambda"
  schedule_expression = "cron(0 6 * * ? *)"
}

resource "aws_lambda_permission" "allow_cloudwatch_trigger" {
  action        = "lambda:InvokeFunction"
  function_name = module.snapshot_reporter.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.snapshot_reporter_rule.arn
}

resource "aws_cloudwatch_event_target" "event_trigger" {
  rule = aws_cloudwatch_event_rule.snapshot_reporter_rule.id
  arn  = module.snapshot_reporter.arn
}
