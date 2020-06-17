output "merged_items_topic_name" {
  value = module.items_merger.topic_name
}

output "merged_items_topic_arn" {
  value = module.items_merger.topic_arn
}

output "merged_bibs_topic_name" {
  value = module.bibs_merger.topic_name
}

output "merged_bibs_topic_arn" {
  value = module.bibs_merger.topic_arn
}

output "vhs_table_name" {
  value = module.vhs_sierra.table_name
}

output "vhs_bucket_name" {
  value = module.vhs_sierra.bucket_name
}

output "vhs_full_access_policy" {
  value = module.vhs_sierra.full_access_policy
}
output "vhs_read_policy" {
  value = module.vhs_sierra.read_policy
}
