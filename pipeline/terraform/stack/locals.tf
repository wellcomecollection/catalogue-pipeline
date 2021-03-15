locals {
  namespace        = "catalogue-${var.pipeline_date}"
  namespace_hyphen = replace(local.namespace, "_", "-")

  es_works_source_index       = "works-source-${var.pipeline_date}"
  es_works_merged_index       = "works-merged-${var.pipeline_date}"
  es_works_identified_index   = "works-identified-${var.pipeline_date}"
  es_works_denormalised_index = "works-denormalised-${var.pipeline_date}"
  es_works_index              = "works"

  es_images_initial_index   = "images-initial-${var.pipeline_date}"
  es_images_augmented_index = "images-augmented-${var.pipeline_date}"
  es_images_index           = "images"

  # The max number of connections allowed by the instance.
  # specified at /infrastructure/critical/rds_id_minter.tf
  base_rds_instances             = 1
  id_minter_rds_max_connections  = (local.base_rds_instances + local.extra_rds_instances) * 45
  id_minter_task_max_connections = min(9, local.max_capacity)

  # We don't want to overload our databases if we're not reindexing
  # and don't have extra database capacity provisioned
  max_capacity = var.is_reindexing ? var.max_capacity : min(3, var.max_capacity)

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
