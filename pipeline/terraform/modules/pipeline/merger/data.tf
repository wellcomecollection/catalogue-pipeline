locals {
  namespace = "catalogue-${var.pipeline_date}_${var.service_name}"

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
}