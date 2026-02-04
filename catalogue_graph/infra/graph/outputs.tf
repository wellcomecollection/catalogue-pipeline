output "neptune_cluster_arn" {
  value = module.catalogue_graph_neptune_cluster.neptune_cluster_arn
}

output "neptune_cluster_resource_id" {
  value = module.catalogue_graph_neptune_cluster.neptune_cluster_resource_id
}

output "neptune_cluster_data_access_arn" {
  value = module.catalogue_graph_neptune_cluster.neptune_cluster_data_access_arn
}

output "neptune_nlb_url_secret_arn" {
  value = module.catalogue_graph_neptune_cluster.neptune_nlb_url_secret_arn
}

output "neptune_cluster_endpoint_secret_arn" {
  value = module.catalogue_graph_neptune_cluster.neptune_cluster_endpoint_secret_arn
}
