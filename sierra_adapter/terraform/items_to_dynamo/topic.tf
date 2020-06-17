module "sierra_to_dynamo_updates_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "${var.namespace}-sierra_items_to_dynamo_updates"
}
