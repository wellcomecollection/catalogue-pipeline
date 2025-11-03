locals {
  namespace_suffix = var.index_date != null ? "-${var.index_date}" : ""
  namespace        = "catalogue-${var.pipeline_date}${local.namespace_suffix}"

  es_upstream_config = {
    es_upstream_host     = var.es_config.es_host
    es_upstream_port     = var.es_config.es_port
    es_upstream_protocol = var.es_config.es_protocol
    es_upstream_apikey   = var.es_config.es_apikey
  }
  es_downstream_config = {
    es_downstream_host     = var.es_config.es_host
    es_downstream_port     = var.es_config.es_port
    es_downstream_protocol = var.es_config.es_protocol
    es_downstream_apikey   = var.es_config.es_apikey
  }

  index_date = var.index_date != null ? var.index_date : var.pipeline_date

  es_works_identified_index   = var.es_index_config.es_works_identified_index != null ? var.es_index_config.es_works_identified_index : "works-identified-${local.index_date}"
  es_works_denormalised_index = var.es_index_config.es_works_denormalised_index != null ? var.es_index_config.es_works_denormalised_index : "works-denormalised-${local.index_date}"
  es_images_initial_index     = var.es_index_config.es_images_initial_index != null ? var.es_index_config.es_images_initial_index : "images-initial-${local.index_date}"
}