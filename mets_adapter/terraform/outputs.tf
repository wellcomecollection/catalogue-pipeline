output "mets_adapter_topic_name" {
  value = module.mets_adapter_output_topic.name
}

output "mets_adapter_topic_arn" {
  value = module.mets_adapter_output_topic.arn
}

output "repopulate_script_topic_arn" {
  value = module.repopulate_script_topic.arn
}
