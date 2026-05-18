output "output_topic_arn" {
  value = module.matcher_output_topic.arn
}

output "lambda_role_name" {
  value = module.matcher_lambda.lambda_role_name
}
