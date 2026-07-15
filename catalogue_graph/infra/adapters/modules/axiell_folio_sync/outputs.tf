output "lambda_function_arn" {
  description = "Lambda function ARN"
  value       = module.sync_lambda.lambda.arn
}

output "lambda_function_name" {
  description = "Lambda function name"
  value       = module.sync_lambda.lambda.function_name
}

output "step_function_arn" {
  description = "ARN of the Axiell to Folio sync Step Function state machine"
  value       = aws_sfn_state_machine.state_machine.arn
}

output "manifest_bucket_name" {
  description = "S3 bucket name for storing NDJSON sync manifests"
  value       = aws_s3_bucket.axiell_folio_sync_manifests.bucket
}
