output "calm_adapter_topic_arn" {
  value = module.calm_adapter_topic.arn
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

output "assumable_read_role" {
  value = module.vhs.assumable_read_role
}
