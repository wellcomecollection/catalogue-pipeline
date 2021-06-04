output "topic_arn" {
  value = module.windows_topic.arn
}

output "topic_publish_policy" {
  value = module.windows_topic.publish_policy
}
