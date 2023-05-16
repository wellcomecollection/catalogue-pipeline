/*output "demultiplexer_arn" {
  value = module.s3_demultiplexer_lambda.arn
}*/

output "topic_arn" {
  value = module.output_topic.arn
}
