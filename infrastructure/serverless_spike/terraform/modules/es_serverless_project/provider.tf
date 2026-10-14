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

# Project credentials are only exported at creation, so the bootstrap key is
# minted here with them and everything downstream authenticates with the key.
provider "elasticstack" {
  elasticsearch {
    username  = ec_elasticsearch_project.project.credentials.username
    password  = ec_elasticsearch_project.project.credentials.password
    endpoints = [ec_elasticsearch_project.project.endpoints.elasticsearch]
  }
}
