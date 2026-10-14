terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.catalogue]
    }
    elasticstack = {
      source  = "elastic/elasticstack"
      version = "0.16.1"
    }
    random = {
      source  = "hashicorp/random"
      version = ">= 3.5.0"
    }
  }
}

provider "elasticstack" {
  elasticsearch {
    api_key   = var.es_cluster.api_key
    username  = var.es_cluster.api_key == null ? var.es_cluster.username : null
    password  = var.es_cluster.api_key == null ? var.es_cluster.password : null
    endpoints = [var.es_cluster.https_endpoint]
  }
}
