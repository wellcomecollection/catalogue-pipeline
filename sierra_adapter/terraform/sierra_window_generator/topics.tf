module "windows_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "sierra_${var.resource_type}_windows"
}
