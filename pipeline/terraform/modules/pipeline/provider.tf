terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"

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
  }
}
