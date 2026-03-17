output "state_machine_arn" {
  description = "ARN of the adapter state machine"
  value       = aws_sfn_state_machine.state_machine.arn
}