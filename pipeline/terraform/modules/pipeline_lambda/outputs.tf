output "lambda_role_name" {
  value = module.pipeline_step.lambda_role.name
}

output "queue_url" {
  value = module.input_queue.url
}
