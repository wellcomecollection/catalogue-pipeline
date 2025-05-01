locals {
  es_works_source_index       = "works-source-${var.local_pipeline_name}"
  es_works_identified_index   = "works-identified-${var.local_pipeline_name}"
  es_works_denormalised_index = "works-denormalised-${var.local_pipeline_name}"
  es_works_index              = "works-indexed-${var.local_pipeline_name}"

  es_images_initial_index   = "images-initial-${var.local_pipeline_name}"
  es_images_augmented_index = "images-augmented-${var.local_pipeline_name}"
  es_images_index           = "images-indexed-${var.local_pipeline_name}"

  es_config_path = "${path.root}/../../../index_config"

}
