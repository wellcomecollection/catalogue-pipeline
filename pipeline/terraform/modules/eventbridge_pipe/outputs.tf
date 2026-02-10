output "pipe_arn" {
  description = "ARN of the EventBridge Pipe"
  value       = aws_pipes_pipe.pipe.arn
}

output "pipe_name" {
  description = "Name of the EventBridge Pipe"
  value       = aws_pipes_pipe.pipe.name
}

output "queue_arn" {
  description = "ARN of the SQS queue"
  value       = module.input_queue.arn
}

output "queue_url" {
  description = "URL of the SQS queue"
  value       = module.input_queue.url
}

output "pipe_role_name" {
  description = "Name of the IAM role used by the EventBridge Pipe"
  value       = aws_iam_role.pipe_role.name
}
