output "https_endpoint" {
  value     = ec_deployment.cluster.elasticsearch.https_endpoint
  sensitive = true
}

output "username" {
  value     = ec_deployment.cluster.elasticsearch_username
  sensitive = true
}

output "password" {
  value     = ec_deployment.cluster.elasticsearch_password
  sensitive = true
}

output "private_host" {
  value = local.secrets_kv_map["elasticsearch/${var.cluster_name}/private_host"]
}

output "port" {
  value = local.secrets_kv_map["elasticsearch/${var.cluster_name}/port"]
}

output "protocol" {
  value = local.secrets_kv_map["elasticsearch/${var.cluster_name}/protocol"]
}
