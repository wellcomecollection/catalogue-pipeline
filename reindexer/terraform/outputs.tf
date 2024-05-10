# This output is used by the `start_reindex.py` script to determine which
# topic it should send requests to.
#
# Don't change it without changing the corresponding script code.
#
output "topic_arn" {
  value = module.reindex_worker.topic_arn
}

output "ebsco_reindexer_topic_arn" {
  value = local.ebsco_reindexer_topic_arn
}
output "tei_reindexer_topic_arn" {
  value = local.tei_reindexer_topic_arn
}

output "mets_reindexer_topic_name" {
  value = local.mets_reindexer_topic_name
}

output "mets_reindexer_topic_arn" {
  value = local.mets_reindexer_topic_arn
}

output "calm_reindexer_topic_name" {
  value = local.calm_reindexer_topic_name
}

output "calm_reindexer_topic_arn" {
  value = local.calm_reindexer_topic_arn
}

output "calm_deletion_checker_topic_name" {
  value = local.calm_deletion_checker_topic_name
}

output "calm_deletion_checker_topic_arn" {
  value = local.calm_deletion_checker_topic_arn
}

output "calm_reporting_topic_arn" {
  value = aws_sns_topic.calm_reindex_reporting.arn
}

