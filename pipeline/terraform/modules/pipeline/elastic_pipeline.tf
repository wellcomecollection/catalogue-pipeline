module "elastic" {
  source = "./elastic"

  pipeline_date                  = var.pipeline_date
  es_cluster_deployment_template = var.es_cluster_deployment_template
  es_node_count                  = local.es_node_count
  es_memory                      = local.es_memory
  network_config                 = local.network_config
  monitoring_config              = local.monitoring_config
  allow_delete_indices           = var.allow_delete_indices
  index_config                   = var.index_config
  catalogue_account_services     = ["catalogue_api", "snapshot_generator", "concepts_api"]
  version_regex                  = var.version_regex

  providers = {
    aws = aws
    aws.catalogue = aws.catalogue
  }
}
