output "state_machine_arn" {
  value = module.state_machine.state_machine_arn
}

output "find_work_lambda_arn" {
  value = module.find_work_lambda.lambda_arn
}

output "find_work_lambda_role_name" {
  value = module.find_work_lambda.lambda_role_name
}
