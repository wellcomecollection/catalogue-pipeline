output "tei_adapter_topic_arn" {
  value = module.tei_adapter_topic.arn
}
output "tei_adapter_bucket_name" {
  value = aws_s3_bucket.tei_adapter.id
}
output "tei_adapter_dynamo_table_name" {
  value = aws_dynamodb_table.tei_adapter_table.id
}
