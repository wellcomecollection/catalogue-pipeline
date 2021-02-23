output "arn" {
  value = aws_lambda_function.scheduled_lambda.arn
}

output "function_name" {
  value = aws_lambda_function.scheduled_lambda.function_name
}

output "role_name" {
  value = aws_iam_role.lambda_role.name
}
