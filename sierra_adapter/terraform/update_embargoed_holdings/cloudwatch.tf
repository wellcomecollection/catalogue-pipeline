resource "aws_cloudwatch_event_rule" "daily_rule" {
  name                = "update_embargoed_holdings_daily_rule"
  description         = "Runs once a day at 2am"
  schedule_expression = "cron(0 2 * * ? *)"
}
