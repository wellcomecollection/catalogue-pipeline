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

  platform_applications = {
    transformer       = ["works-source_write"]
    id_minter         = ["works-source_read", "works-identified_write"]
    matcher           = ["works-identified_read"]
    merger            = ["works-identified_read", "works-merged_write", "images-initial_write"]
    router            = ["works-merged_read", "works-denormalised_write"]
    path_concatenator = ["works-merged_read", "works-merged_write"]
    relation_embedder = ["works-merged_read", "works-denormalised_write"]
    work_ingestor     = ["works-denormalised_read", "works-indexed_write"]
    inferrer          = ["images-initial_read", "images-augmented_write"]
    image_ingestor    = ["images-augmented_read", "images-indexed_write"]

    # This role isn't used by applications, but instead provided to give developer scripts
    # read-only access to the pipeline_storage cluster.
    read_only = concat(
      [
        for index in local.works_indices : "works-${index}_read"
      ],
      [
        for index in local.images_indices : "images-${index}_read"
      ],
      ["viewer"],
    )
  }

  catalogue_applications = {
    snapshot_generator = ["works-indexed_read"]
    catalogue_api = [
      "works-indexed_read",
      "images-indexed_read",
      # This role allows the API to fetch index mappings, which it uses
      # to check internal model compatibility.
      # See https://github.com/wellcomecollection/catalogue-api/tree/main/internal_model_tool
      # TODO: Do we still need this?
      "viewer",
    ]
    concepts_api = [
      "works-indexed_read"
    ]
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

module "platform_users" {
  source = "../modules/elastic_user"

  for_each = toset(keys(local.platform_applications))

  name  = each.key
  roles = local.platform_applications[each.key]

  pipeline_date = var.pipeline_date
}

module "catalogue_users" {
  source = "../modules/elastic_user"

  providers = {
    aws = aws.catalogue
  }

  for_each = toset(keys(local.catalogue_applications))

  name  = each.key
  roles = local.catalogue_applications[each.key]

  pipeline_date = var.pipeline_date
}

locals {
  pipeline_storage_service_list = [
    "id_minter",
    "matcher",
    "merger",
    "transformer",
    "path_concatenator",
    "relation_embedder",
    "router",
    "inferrer",
    "work_ingestor",
    "image_ingestor",
  ]

  pipeline_storage_es_service_secrets = zipmap(local.pipeline_storage_service_list, [
    for service in local.pipeline_storage_service_list :
    {
      es_host     = local.pipeline_storage_private_host
      es_port     = local.pipeline_storage_port
      es_protocol = local.pipeline_storage_protocol
      es_username = "elasticsearch/pipeline_storage_${var.pipeline_date}/${service}/es_username"
      es_password = "elasticsearch/pipeline_storage_${var.pipeline_date}/${service}/es_password"
    }
  ])
}
