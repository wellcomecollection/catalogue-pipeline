# The lambda source is updated much less frequently than once a day (every 7 days)
# but it's still a good idea to trigger the lambda to run at least once a day to ensure
# that it's always up to date.
resource "aws_cloudwatch_event_rule" "every_day_at_6am" {
  name                = "trigger_ftp_lambda"
  schedule_expression = "cron(0 6 * * ? *)"
}

resource "aws_lambda_permission" "allow_reporter_cloudwatch_trigger" {
  action        = "lambda:InvokeFunction"
  function_name = module.ftp_lambda.lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.every_day_at_6am.arn
}

resource "aws_cloudwatch_event_target" "event_trigger" {
  rule = aws_cloudwatch_event_rule.every_day_at_6am.name
  arn  = module.ftp_lambda.lambda.arn
}
