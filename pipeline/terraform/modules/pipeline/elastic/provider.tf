terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = ">= 5.0"
      configuration_aliases = [aws.catalogue]
    }

    ec = {
      source  = "elastic/ec"
      version = "0.2.1"
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
    username  = ec_deployment.pipeline.elasticsearch_username
    password  = ec_deployment.pipeline.elasticsearch_password
    endpoints = [ec_deployment.pipeline.elasticsearch[0].https_endpoint]
  }
}
