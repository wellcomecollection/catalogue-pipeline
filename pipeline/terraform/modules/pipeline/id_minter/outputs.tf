output "id_generator_lambda_arn" {
  value = module.id_generator_lambda.lambda_arn
}

output "id_minter_lambda_role_name" {
  value = module.id_minter_lambda.lambda_role_name
}
