locals {
  reporting_account_id = "269807742353"
}

module "output_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "${var.namespace}-sierra_${var.resource_type}_merger_output"

  cross_account_subscription_ids = [local.reporting_account_id]
}
