output "mets_adapter_topic_name" {
  value = module.mets_adapter_output_topic.name
}

output "mets_adapter_topic_arn" {
  value = module.mets_adapter_output_topic.arn
}

output "repopulate_script_topic_arn" {
  value = module.repopulate_script_topic.arn
}
# Mets Store

output "mets_dynamo_read_policy" {
  value = data.aws_iam_policy_document.mets_dynamo_read_policy.json
}

output "mets_dynamo_table_name" {
  value = aws_dynamodb_table.mets_adapter_table.id
}

