output "state_machine_arn" {
  description = "ARN of the EBSCO adapter state machine"
  value       = aws_sfn_state_machine.state_machine.arn
}

output "state_machine_execution_url" {
  description = "URL to view state machine executions in AWS Console"
  value       = "https://console.aws.amazon.com/states/home?region=${data.aws_region.current.region}#/statemachines/view/${aws_sfn_state_machine.state_machine.arn}"
}
