# Output maps keyed by graph_date.

# Note: the legacy production cluster uses an empty string ("") graph_date, so prod is keyed by "" in these maps.
output "neptune_cluster_arns" {
  value = {for cluster in local.neptune_clusters : cluster.graph_date => cluster.neptune_cluster_arn}
}

output "neptune_cluster_resource_ids" {
  value = {for cluster in local.neptune_clusters : cluster.graph_date => cluster.neptune_cluster_resource_id}
}

output "neptune_cluster_endpoint_secret_arns" {
  value = {for cluster in local.neptune_clusters : cluster.graph_date => cluster.neptune_cluster_endpoint_secret_arn}
}

output "neptune_cluster_data_access_arns" {
  value = {for cluster in local.neptune_clusters : cluster.graph_date => cluster.neptune_cluster_data_access_arn}
}
