output "arn" {
  description = "ARN of the Lambda function"
  value       = module.lambda_function.lambda.arn
}

output "function_name" {
  description = "Name of the Lambda function"
  value       = module.lambda_function.lambda.function_name
}

output "role_name" {
  description = "Name of the IAM role for this Lambda"
  value       = module.lambda_function.lambda_role.name
}
