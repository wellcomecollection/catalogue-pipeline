resource "aws_cloudwatch_event_rule" "snapshot_scheduler_rule" {
  name                = "snapshot_scheduler_rule-${var.deployment_service_env}"
  description         = "Starts the snapshot_scheduler (${var.deployment_service_env}) lambda"
  schedule_expression = "rate(1 day)"
}
