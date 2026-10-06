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
    username  = var.es_cluster.username
    password  = var.es_cluster.password
    endpoints = [var.es_cluster.https_endpoint]
  }
}
