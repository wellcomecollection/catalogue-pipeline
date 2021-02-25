module "sierra_to_dynamo_updates_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "${var.namespace}-sierra_${var.resource_type}_to_dynamo_updates"
}
