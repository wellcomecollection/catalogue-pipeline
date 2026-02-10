output "state_machine_arn" {
  description = "ARN of the id_minter state machine"
  value       = module.state_machine.state_machine_arn
}

output "state_machine_role_name" {
  description = "Name of the state machine IAM role"
  value       = module.state_machine.state_machine_role_name
}

output "pipe_arns" {
  description = "Map of SNS topic ARN to EventBridge Pipe ARN"
  value       = { for k, v in module.eventbridge_pipe : k => v.pipe_arn }
}

output "queue_arns" {
  description = "Map of SNS topic ARN to SQS queue ARN"
  value       = { for k, v in module.eventbridge_pipe : k => v.queue_arn }
}
