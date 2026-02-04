output "neptune_cluster_arn" {
  value = aws_neptune_cluster.catalogue_graph_cluster.arn
}

output "neptune_cluster_resource_id" {
  value = aws_neptune_cluster.catalogue_graph_cluster.cluster_resource_id
}

output "neptune_cluster_data_access_arn" {
  value = local.cluster_data_access_arn
}

output "neptune_nlb_url_secret_arn" {
  value = aws_secretsmanager_secret.neptune_nlb_url.arn
}

output "neptune_cluster_endpoint_secret_arn" {
  value = aws_secretsmanager_secret.neptune_cluster_endpoint.arn
}
