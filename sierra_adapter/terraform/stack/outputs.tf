output "merged_items_topic_arn" {
  value = module.items_merger.topic_arn
}

output "merged_bibs_topic_arn" {
  value = module.bibs_merger.topic_arn
}

output "merged_holdings_topic_arn" {
  value = module.holdings_merger.topic_arn
}

output "merged_orders_topic_arn" {
  value = module.orders_merger.topic_arn
}

output "vhs_table_name" {
  value = module.vhs_sierra.table_name
}

output "vhs_bucket_name" {
  value = module.vhs_sierra.bucket_name
}

output "vhs_read_policy" {
  value = module.vhs_sierra.read_policy
}

output "vhs_assumable_read_role" {
  value = module.vhs_sierra.assumable_read_role
}
