locals {
  tei_suffix                  = var.toggle_tei_on ? "tei-on" : "tei-off"
  namespace                   = "${var.namespace_hyphen}-${local.tei_suffix}"
  es_works_merged_index       = var.toggle_tei_on ? "${var.es_works_merged_index}-${local.tei_suffix}" : var.es_works_merged_index
  es_works_denormalised_index = var.toggle_tei_on ? "${var.es_works_denormalised_index}-${local.tei_suffix}" : var.es_works_denormalised_index
  es_images_initial_index     = var.toggle_tei_on ? "${var.es_images_initial_index}-${local.tei_suffix}" : var.es_images_initial_index
  es_images_augmented_index   = var.toggle_tei_on ? "${var.es_images_augmented_index}-${local.tei_suffix}" : var.es_images_augmented_index
  es_images_index             = var.toggle_tei_on ? "${var.es_images_index}-${local.tei_suffix}" : var.es_images_index
  es_works_index              = var.toggle_tei_on ? "${var.es_works_index}-${local.tei_suffix}" : var.es_works_index
}
