output "calm_adapter_topic_arn" {
  value = aws_sns_topic.calm_adapter_topic.arn
}

output "vhs_read_policy" {
  value = module.vhs.read_policy
}

output "vhs_bucket_name" {
  value = module.vhs.bucket_name
}

output "vhs_table_name" {
  value = module.vhs.table_name
}
