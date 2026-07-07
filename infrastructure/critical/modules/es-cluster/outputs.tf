output "https_endpoint" {
  value     = ec_deployment.cluster.elasticsearch.https_endpoint
  sensitive = true
}

output "username" {
  value     = local.secrets_kv_map["elasticsearch/es-cluster-${var.cluster_date}/es_username"]
  sensitive = true
}

output "password" {
  value     = local.secrets_kv_map["elasticsearch/es-cluster-${var.cluster_date}/es_password"]
  sensitive = true
}

output "private_host" {
  value = local.secrets_kv_map["elasticsearch/es-cluster-${var.cluster_date}/private_host"]
}

output "port" {
  value = local.secrets_kv_map["elasticsearch/es-cluster-${var.cluster_date}/port"]
}

output "protocol" {
  value = local.secrets_kv_map["elasticsearch/es-cluster-${var.cluster_date}/protocol"]
}
