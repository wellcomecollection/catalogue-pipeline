terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.catalogue]
    }

    elasticstack = {
      source = "elastic/elasticstack"
    }
  }
}
