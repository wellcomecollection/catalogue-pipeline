locals {
  public_url  = ec_elasticsearch_project.project.endpoints.elasticsearch
  public_host = regex("^https?://([^:/]+)", local.public_url)[0]

  private_url  = try(ec_elasticsearch_project.project.private_endpoints.elasticsearch, null)
  private_host = local.private_url == null ? local.public_host : regex("^https?://([^:/]+)", local.private_url)[0]
}

# The same shape infrastructure/critical emits for a hosted cluster, so the
# elastic_indices module takes it unchanged. Port is the literal 443 because
# the serverless URL may carry no port suffix to split on.
output "es_cluster" {
  sensitive = true
  value = {
    https_endpoint     = local.public_url
    username           = ec_elasticsearch_project.project.credentials.username
    password           = ec_elasticsearch_project.project.credentials.password
    private_host       = local.private_host
    public_host        = local.public_host
    port               = "443"
    protocol           = "https"
    read_only_username = null
    read_only_password = null
    api_key            = elasticstack_elasticsearch_security_api_key.bootstrap.encoded
  }
}

output "project_id" {
  value = ec_elasticsearch_project.project.id
}

output "endpoints" {
  value = {
    public  = local.public_url
    private = local.private_url
    kibana  = ec_elasticsearch_project.project.endpoints.kibana
  }
}

output "search_lake" {
  description = "Read back after creation and again after the On-demand tier is set in the console."
  value       = ec_elasticsearch_project.project.search_lake
}
