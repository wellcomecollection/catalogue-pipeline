resource "aws_cloudwatch_event_rule" "window_generator_rule" {
  name                = "tei_window_generator_rule"
  description         = "Starts the ${module.window_generator_lambda.function_name} lambda"
  schedule_expression = "rate(${var.trigger_interval_minutes} minutes)"
}
