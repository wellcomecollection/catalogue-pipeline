module "mets_reindexer_topic" {
  source                         = "git::https://github.com/wellcometrust/terraform.git//sns?ref=v19.13.2"
  name                           = "mets_reindexer_topic"
}
