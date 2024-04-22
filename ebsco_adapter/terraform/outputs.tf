output "ebsco_adapter_topic_name" {
  value = module.ebsco_adapter_output_topic.name
}

output "ebsco_adapter_topic_arn" {
  value = module.ebsco_adapter_output_topic.arn
}

output "ebsco_adapter_bucket_name" {
  value = aws_s3_bucket.ebsco_adapter.bucket
}