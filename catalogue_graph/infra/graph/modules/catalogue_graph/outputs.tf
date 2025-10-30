output "neptune_cluster_arn" {
  value = aws_neptune_cluster.catalogue_graph_cluster.arn
}

output "neptune_cluster_resource_id" {
  value = aws_neptune_cluster.catalogue_graph_cluster.cluster_resource_id
}

output "neptune_cluster_data_access_arn" {
  value = local.cluster_data_access_arn
}
