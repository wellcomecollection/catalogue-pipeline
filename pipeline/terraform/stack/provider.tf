terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"

      configuration_aliases = [aws.catalogue]
    }

    ec = {
      source  = "elastic/ec"
      version = "0.4.1"
    }

    elasticstack = {
      source  = "elastic/elasticstack"
      version = "0.3.3"
    }
  }
}
