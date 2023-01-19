output "arn" {
  value = module.scheduled_lambda.lambda.arn
}

output "function_name" {
  value = module.scheduled_lambda.lambda.function_name
}

output "role_name" {
  value = module.scheduled_lambda.lambda_role.name
}
