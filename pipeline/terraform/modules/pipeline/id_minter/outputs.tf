output "id_generator_lambda_arn" {
  value = var.include_id_generator ? module.id_generator_lambda[0].lambda_arn : null
}

output "id_minter_lambda_arn" {
  value = module.id_minter_lambda.lambda_arn
}

output "id_minter_lambda_role_name" {
  value = module.id_minter_lambda.lambda_role_name
}

output "id_minter_output_topic_arn" {
  value = module.id_minter_output_topic.arn
}
