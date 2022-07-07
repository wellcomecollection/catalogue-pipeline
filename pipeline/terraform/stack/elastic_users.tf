provider "elasticstack" {
  # TODO: Does this mean the username/password end up in the Terraform state?
  #
  # Should we retrieve them from Secrets Manager instead?  Is that possible?
  elasticsearch {
    username  = ec_deployment.pipeline.elasticsearch_username
    password  = ec_deployment.pipeline.elasticsearch_password
    endpoints = ec_deployment.pipeline.elasticsearch.*.https_endpoint
  }
}

locals {
  works_indices = [
    "source",
    "merged",
    "denormalised",
    "identified",
    "indexed",
  ]

  images_indices = [
    "initial",
    "augmented",
    "indexed",
  ]

  applications = {
    transformer = ["works-source_write"]
  }
}

resource "elasticstack_elasticsearch_security_role" "works_read" {
  for_each = toset(local.works_indices)

  name = "works-${each.key}_read"

  indices {
    names      = ["works-${each.key}*"]
    privileges = ["read"]
  }
}

resource "elasticstack_elasticsearch_security_role" "works_write" {
  for_each = toset(local.works_indices)

  name = "works-${each.key}_write"

  indices {
    names      = ["works-${each.key}*"]
    privileges = ["all"]
  }
}

resource "elasticstack_elasticsearch_security_role" "images_read" {
  for_each = toset(local.images_indices)

  name = "images-${each.key}_read"

  indices {
    names      = ["images-${each.key}*"]
    privileges = ["read"]
  }
}

resource "elasticstack_elasticsearch_security_role" "images_write" {
  for_each = toset(local.images_indices)

  name = "images-${each.key}_write"

  indices {
    names      = ["images-${each.key}*"]
    privileges = ["all"]
  }
}

module "users" {
  source = "../modules/elastic_user"

  for_each = toset(keys(local.applications))

  name  = each.key
  roles = local.applications[each.key]

  pipeline_date = var.pipeline_date
}
