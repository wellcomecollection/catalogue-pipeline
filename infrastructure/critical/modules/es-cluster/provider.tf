terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.catalogue]
    }
    ec = {
      source = "elastic/ec"
    }
    elasticstack = {
      source = "elastic/elasticstack"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

provider "elasticstack" {
  elasticsearch {
    username  = ec_deployment.cluster.elasticsearch_username
    password  = ec_deployment.cluster.elasticsearch_password
    endpoints = [ec_deployment.cluster.elasticsearch.https_endpoint]
  }
}
