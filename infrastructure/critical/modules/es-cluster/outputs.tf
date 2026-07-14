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
  value = "${local.elastic_id}.vpce.${local.elastic_region}.aws.elastic-cloud.com"
}

output "public_host" {
  value = "${local.elastic_id}.${local.elastic_region}.aws.found.io"
}

output "port" {
  value = reverse(split(":", ec_deployment.cluster.elasticsearch.https_endpoint))[0]
}

output "protocol" {
  value = split(":", ec_deployment.cluster.elasticsearch.https_endpoint)[0]
}

output "read_only_username" {
  value = elasticstack_elasticsearch_security_user.read_only.username
}

output "read_only_password" {
  value     = random_password.read_only_user.result
  sensitive = true
}
