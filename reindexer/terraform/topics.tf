module "mets_reindexer_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "mets_reindexer_topic"
}

module "calm_reindexer_topic" {
  source                         = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name                           = "calm_reindexer_topic"
  cross_account_subscription_ids = ["269807742353"]
}
