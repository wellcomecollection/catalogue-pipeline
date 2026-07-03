terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.catalogue]
    }
    elasticstack = {
      source  = "elastic/elasticstack"
      version = "0.7.0"
    }
    random = {
      source  = "hashicorp/random"
      version = ">= 3.5.0"
    }
  }
}

provider "elasticstack" {
  elasticsearch {
    username  = var.es_username
    password  = var.es_password
    endpoints = [var.es_endpoint]
  }
}
