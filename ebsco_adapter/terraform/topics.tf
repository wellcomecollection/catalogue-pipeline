module "ebsco_adapter_output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "ebsco_adapter_topic"
}
