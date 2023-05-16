terraform {
  required_providers {
    elasticstack = {
      source = "elastic/elasticstack"
    }
  }
}


provider "elasticstack" {
  elasticsearch {
    endpoints = ["http://localhost:9200"]
    insecure = true
  }
}
