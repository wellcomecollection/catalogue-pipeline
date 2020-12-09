locals {
  sierra_reader_image          = "${var.sierra_reader_image}:env.${var.deployment_env}"
  sierra_bib_merger_image      = "${var.sierra_bib_merger_image}:env.${var.deployment_env}"
  sierra_item_merger_image     = "${var.sierra_item_merger_image}:env.${var.deployment_env}"
  sierra_items_to_dynamo_image = "${var.sierra_items_to_dynamo_image}:env.${var.deployment_env}"
}
