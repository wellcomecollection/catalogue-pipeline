output "works_output_topic_arn" {
  description = "The ARN of the merger works output topic"
  value       = module.merger_works_output_topic.arn
}

output "works_path_output_topic_arn" {
  description = "The ARN of the merger works with path output topic"
  value       = module.merger_works_path_output_topic.arn
}

output "works_incomplete_path_output_topic_arn" {
  description = "The ARN of the merger works incomplete path output topic"
  value       = module.merger_works_incomplete_path_output_topic.arn
}

output "images_output_topic_arn" {
  description = "The ARN of the merger images output topic"
  value       = module.merger_images_output_topic.arn
}