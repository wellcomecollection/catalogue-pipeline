resource "aws_secretsmanager_secret" "neptune_cluster_endpoint" {
  name = "${var.namespace}/neptune-cluster-endpoint"
}

resource "aws_secretsmanager_secret_version" "neptune_cluster_endpoint_value" {
  secret_id     = aws_secretsmanager_secret.neptune_cluster_endpoint.id
  secret_string = aws_neptune_cluster.catalogue_graph_cluster.endpoint
}
