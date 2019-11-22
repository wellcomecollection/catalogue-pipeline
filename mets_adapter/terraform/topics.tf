module "mets_vhs_keys_topic" {
  source                         = "git::https://github.com/wellcometrust/terraform.git//sns?ref=v19.13.2"
  name                           = "mets_vhs_keys_topic"
}

// TODO: delete this when we get a topic from the storage service
module "temp_test_topic" {
  source                         = "git::https://github.com/wellcometrust/terraform.git//sns?ref=v19.13.2"
  name                           = "mets_temp_test_topic"
}