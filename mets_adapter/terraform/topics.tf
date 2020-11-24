module "mets_adapter_output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "mets_adapter_topic"
}

module "repopulate_script_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "mets_adapter_repopulate_script_output"
}
