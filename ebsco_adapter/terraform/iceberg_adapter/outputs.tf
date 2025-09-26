output "state_machine_arn" {
  description = "ARN of the EBSCO adapter state machine"
  value       = aws_sfn_state_machine.state_machine.arn
}

output "daily_schedule_rule_name" {
  description = "Name of the daily schedule EventBridge rule"
  value       = aws_cloudwatch_event_rule.daily_schedule.name
}

output "state_machine_execution_url" {
  description = "URL to view state machine executions in AWS Console"
  value       = "https://console.aws.amazon.com/states/home?region=${data.aws_region.current.name}#/statemachines/view/${aws_sfn_state_machine.state_machine.arn}"
}
