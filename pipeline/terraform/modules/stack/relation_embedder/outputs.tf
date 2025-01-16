output "router_work_output_topic_arn" {
  value = module.router_work_output_topic.arn
}

output "relation_embedder_output_topic_arn" {
  value = module.embedder_lambda_output_topic.arn
}
