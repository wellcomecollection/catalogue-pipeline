module "demultiplexer_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "sierra_demultiplexed_${var.resource_type}"
}
