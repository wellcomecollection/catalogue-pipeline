terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    elasticstack = {
      source  = "elastic/elasticstack"
      version = "0.3.3"
    }
  }
}
