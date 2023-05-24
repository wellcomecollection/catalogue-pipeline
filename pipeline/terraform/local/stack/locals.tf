locals {
  es_works_source_index       = "works-source-${var.pipeline_date}"
  es_works_merged_index       = "works-merged-${var.pipeline_date}"
  es_works_identified_index   = "works-identified-${var.pipeline_date}"
  es_works_denormalised_index = "works-denormalised-${var.pipeline_date}"
  es_works_index              = "works-indexed-${var.pipeline_date}"

  es_images_initial_index   = "images-initial-${var.pipeline_date}"
  es_images_augmented_index = "images-augmented-${var.pipeline_date}"
  es_images_index           = "images-indexed-${var.pipeline_date}"
}
