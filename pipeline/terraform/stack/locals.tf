locals {
  namespace        = "catalogue-${var.pipeline_date}"
  namespace_hyphen = replace(local.namespace, "_", "-")

  es_works_index              = "works-${var.pipeline_date}"
  es_images_index             = "images-${var.pipeline_date}"
  es_works_merged_index       = "works-merged-${var.pipeline_date}"
  es_works_denormalised_index = "works-denormalised-${var.pipeline_date}"

  id_minter_service_count        = 2 // images and works
  id_minter_task_max_connections = 9
  // The max number of connections allowed by the instance
  // specified at /infrastructure/critical/rds_id_minter.tf
  id_minter_rds_max_connections = 90
}
