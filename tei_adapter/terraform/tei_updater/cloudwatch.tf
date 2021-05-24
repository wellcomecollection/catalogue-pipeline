resource "aws_cloudwatch_event_rule" "tei_updater_rule" {
  name                = "tei_updater_rule"
  description         = "Starts the tei_updater lambda"
  schedule_expression = "rate(${var.trigger_interval_minutes} minutes)"
}
