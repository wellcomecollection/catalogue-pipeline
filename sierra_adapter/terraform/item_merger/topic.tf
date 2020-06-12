module "sierra_item_merger_results" {
  source                         = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name                           = "${var.namespace}-sierra_item_merger_results"
  cross_account_subscription_ids = ["269807742353"]
}
