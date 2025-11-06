output "state_machine_arn" {
  description = "ARN of the created Step Functions state machine"
  value       = aws_sfn_state_machine.state_machine.arn
}

output "state_machine_role_name" {
  description = "Name of the role assumed by the Step Functions state machine"
  value       = aws_iam_role.state_machine_role.name
}

