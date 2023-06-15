locals {
  sierra_linker_image  = "${var.sierra_linker_image}:env.${var.deployment_env}"
  sierra_merger_image  = "${var.sierra_merger_image}:env.${var.deployment_env}"
  sierra_indexer_image = "${var.sierra_indexer_image}:env.${var.deployment_env}"
}
