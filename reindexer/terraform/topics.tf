module "mets_reindexer_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "mets_reindexer_topic"
}
