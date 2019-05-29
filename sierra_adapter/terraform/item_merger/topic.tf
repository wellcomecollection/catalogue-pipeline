module "sierra_item_merger_results" {
  source                         = "git::https://github.com/wellcometrust/terraform.git//sns?ref=v19.13.2"
  name                           = "sierra_item_merger_results"
  cross_account_subscription_ids = ["269807742353"]
}
