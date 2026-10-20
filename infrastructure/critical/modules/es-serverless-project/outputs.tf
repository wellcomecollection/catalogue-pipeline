locals {
  public_url  = ec_elasticsearch_project.project.endpoints.elasticsearch
  public_host = regex("^https?://([^:/]+)", local.public_url)[0]

  private_url  = try(ec_elasticsearch_project.project.private_endpoints.elasticsearch, null)
  private_host = local.private_url == null ? local.public_host : regex("^https?://([^:/]+)", local.private_url)[0]
}

# The shape the hosted es-cluster module emits, so elastic_indices takes it
# unchanged. Port is literal: the serverless URL has no port suffix to split.
# Terraform keeps the creation-time credentials, since a request authenticated
# by an API key cannot mint the per-service keys. Serverless has no native
# realm, so there is no read_only user.
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
  }
}

output "project_id" {
  value = ec_elasticsearch_project.project.id
}
