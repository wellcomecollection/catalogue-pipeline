resource "aws_cloudwatch_event_rule" "window_generator_rule" {
  name                = "${var.source_name}_window_generator_rule"
  description         = "Starts the ${var.source_name}_window_generator lambda"
  schedule_expression = "rate(${var.trigger_interval_minutes} minutes)"
}
