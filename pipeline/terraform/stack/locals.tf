locals {
  namespace        = "catalogue-${var.pipeline_date}"
  namespace_hyphen = replace(local.namespace, "_", "-")

  es_works_index              = "works-${var.pipeline_date}"
  es_images_index             = "images-${var.pipeline_date}"
  es_works_source_index       = "works-source-${var.pipeline_date}"
  es_works_merged_index       = "works-merged-${var.pipeline_date}"
  es_works_identified_index   = "works-identified-${var.pipeline_date}"
  es_works_denormalised_index = "works-denormalised-${var.pipeline_date}"

  # The max number of connections allowed by the instance
  # specified at /infrastructure/critical/rds_id_minter.tf
  id_minter_rds_max_connections  = 2 * 45
  id_minter_task_max_connections = min(9, var.max_capacity)

  services = [
    "ingestor_works",
    "ingestor_images",
    "matcher",
    "merger",
    "id_minter",
    "inference_manager",
    "feature_inferrer",
    "feature_training",
    "palette_inferrer",
    "router",
    "batcher",
    "relation_embedder",
    "transformer_miro",
    "transformer_mets",
    "transformer_sierra",
    "transformer_calm",
  ]
}
