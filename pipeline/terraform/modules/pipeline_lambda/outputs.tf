output "lambda_role_name" {
  value = module.pipeline_step.lambda_role.name
}

output "queue_url" {
  value = length(module.input_queue) > 0 ? module.input_queue[0].url : null
}

output "lambda_arn" {
  value = module.pipeline_step.lambda.arn
}