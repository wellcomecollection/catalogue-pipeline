terraform {
  required_providers {
    ec = {
      source = "elastic/ec"
    }
    elasticstack = {
      source = "elastic/elasticstack"
    }
  }
}

# Only here so Terraform can destroy the bootstrap key the first design
# created. Remove once that destroy has applied.
provider "elasticstack" {
  elasticsearch {
    username  = ec_elasticsearch_project.project.credentials.username
    password  = ec_elasticsearch_project.project.credentials.password
    endpoints = [ec_elasticsearch_project.project.endpoints.elasticsearch]
  }
}
