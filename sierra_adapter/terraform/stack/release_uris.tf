locals {
  sierra_reader_image          = "${var.sierra_reader_image}:env.${var.deployment_env}"
  sierra_bib_merger_image      = "${var.sierra_bib_merger_image}:env.${var.deployment_env}"
  sierra_item_merger_image     = "${var.sierra_item_merger_image}:env.${var.deployment_env}"

  sierra_linker_image = "${var.sierra_linker_image}:env.${var.deployment_env}"
}
