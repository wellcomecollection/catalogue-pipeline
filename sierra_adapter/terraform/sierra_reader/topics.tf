module "output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "${var.namespace}-sierra-reader-${var.resource_type}-output"
}
