data "ec_stack" "latest_patch" {
  version_regex = "9.1.x"
  region        = "eu-west-1"
}

resource "ec_deployment" "pipeline" {
  name = "pipeline-${var.pipeline_date}"

  version = data.ec_stack.latest_patch.version

  region                 = data.ec_stack.latest_patch.region
  deployment_template_id = var.es_cluster_deployment_template

  traffic_filter = local.network_config.traffic_filters

  elasticsearch {
    topology {
      id         = "hot_content"
      zone_count = local.es_node_count
      size       = local.es_memory
    }
  }

  kibana {
    topology {
      zone_count = 1
      size       = "1g"
    }
  }

  observability {
    deployment_id = local.monitoring_config.logging_cluster_id
  }

  lifecycle {
    ignore_changes = [

      # We've had issues in the past when an Elastic upgrade is triggered
      # unexpectedly, sometimes causing missing master nodes/search issues.
      # e.g. https://wellcome.slack.com/archives/C01FBFSDLUA/p1663849437942949
      #
      # This `ignore_changes` means Terraform won't upgrade the cluster
      # version after initial creation -- we'll have to log in to the
      # Elastic Cloud console and trigger it explicitly.
      version,
    ]
  }
}

locals {
  pipeline_storage_elastic_id     = ec_deployment.pipeline.elasticsearch[0].resource_id
  pipeline_storage_elastic_region = ec_deployment.pipeline.elasticsearch[0].region

  pipeline_storage_public_host  = "elasticsearch/pipeline_storage_${var.pipeline_date}/public_host"
  pipeline_storage_private_host = "elasticsearch/pipeline_storage_${var.pipeline_date}/private_host"
  pipeline_storage_protocol     = "elasticsearch/pipeline_storage_${var.pipeline_date}/protocol"
  pipeline_storage_port         = "elasticsearch/pipeline_storage_${var.pipeline_date}/port"
  pipeline_storage_es_username  = "elasticsearch/pipeline_storage_${var.pipeline_date}/es_username"
  pipeline_storage_es_password  = "elasticsearch/pipeline_storage_${var.pipeline_date}/es_password"
}

module "pipeline_storage_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.3.0"

  key_value_map = {
    (local.pipeline_storage_public_host) = "${local.pipeline_storage_elastic_id}.${local.pipeline_storage_elastic_region}.aws.found.io"
    (local.pipeline_storage_es_username) = ec_deployment.pipeline.elasticsearch_username
    (local.pipeline_storage_es_password) = ec_deployment.pipeline.elasticsearch_password

    # The endpoint value is of the form https://{deployment_id}.eu-west-1.aws.found.io:9243
    # We could hard-code these values as "https" and "9243", but we can just as easily infer
    # them from the actual endpoint value.
    (local.pipeline_storage_protocol) = split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint)[0]
    (local.pipeline_storage_port)     = reverse(split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint))[0]

    # See https://www.elastic.co/guide/en/cloud/current/ec-traffic-filtering-vpc.html
    (local.pipeline_storage_private_host) = "${local.pipeline_storage_elastic_id}.vpce.${local.pipeline_storage_elastic_region}.aws.elastic-cloud.com"
  }

  deletion_mode = "IMMEDIATE"
}

module "pipeline_storage_secrets_catalogue" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.3.0"

  providers = {
    aws = aws.catalogue
  }

  key_value_map = {
    (local.pipeline_storage_public_host) = "${local.pipeline_storage_elastic_id}.${local.pipeline_storage_elastic_region}.aws.found.io"
    (local.pipeline_storage_es_username) = ec_deployment.pipeline.elasticsearch_username
    (local.pipeline_storage_es_password) = ec_deployment.pipeline.elasticsearch_password

    # The endpoint value is of the form https://{deployment_id}.eu-west-1.aws.found.io:9243
    # We could hard-code these values as "https" and "9243", but we can just as easily infer
    # them from the actual endpoint value.
    (local.pipeline_storage_protocol) = split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint)[0]
    (local.pipeline_storage_port)     = reverse(split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint))[0]

    # See https://www.elastic.co/guide/en/cloud/current/ec-traffic-filtering-vpc.html
    (local.pipeline_storage_private_host) = "${local.pipeline_storage_elastic_id}.vpce.${local.pipeline_storage_elastic_region}.aws.elastic-cloud.com"
  }

  deletion_mode = "IMMEDIATE"
}

locals {
  es_config_path = "${path.root}/../../../index_config"

  works_source_list        = [for k, v in var.index_config.works.source       : { name = "works-source-${k}",          mappings_name = v }]
  works_denormalised_list  = [for k, v in var.index_config.works.denormalised : { name = "works-denormalised-${k}",    mappings_name = v }]
  works_identified_list    = [for k, v in var.index_config.works.identified   : { name = "works-identified-${k}",      mappings_name = v }]
  works_indexed_list       = [for k, v in var.index_config.works.indexed      : { name = "works-indexed-${k}",         mappings_name = v }]

  images_initial_list      = [for k, v in var.index_config.images.initial    : { name = "images-initial-${k}",        mappings_name = v }]
  images_augmented_list    = [for k, v in var.index_config.images.augmented  : { name = "images-augmented-${k}",      mappings_name = v }]
  images_indexed_list      = [for k, v in var.index_config.images.indexed    : { name = "images-indexed-${k}",        mappings_name = v }]

  concepts_indexed_list    = [for k, v in var.index_config.concepts.indexed  : { name = "concepts-indexed-${k}",      mappings_name = v }]

  index_list = concat(
    local.works_source_list,
    local.works_denormalised_list,
    local.works_identified_list,
    local.works_indexed_list,
    local.images_initial_list,
    local.images_augmented_list,
    local.images_indexed_list,
    local.concepts_indexed_list,
  )

  index_definitions = { for i in local.index_list : i.name => i }

  // Default index names for services that need to know where to write/read from
  es_works_source_index       = "works-source-${var.pipeline_date}"
  es_works_identified_index   = "works-identified-${var.pipeline_date}"
  es_works_denormalised_index = "works-denormalised-${var.pipeline_date}"
  es_works_index              = "works-indexed-${var.pipeline_date}"
  es_concepts_index           = "concepts-indexed-${var.pipeline_date}"
  es_images_initial_index     = "images-initial-${var.pipeline_date}"
  es_images_augmented_index   = "images-augmented-${var.pipeline_date}"
  es_images_index             = "images-indexed-${var.pipeline_date}"
}

module "indices" {
  for_each = local.index_definitions

  source        = "../../es_index"
  name          = each.value.name
  mappings_name = each.value.mappings_name
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete_indices
}

locals {
  service_index_permissions = {
    read_only = {
      read  = ["*"]
      write = []
    }
    transformer = {
      read  = []
      write = [ for idx in local.works_source_list : idx.name ]
    }
    id_minter = {
      read  = [ for idx in local.works_identified_list : idx.name ]
      write = [ for idx in local.works_identified_list : idx.name ]
    }
    matcher = {
      read  = [ for idx in local.works_identified_list : idx.name ]
      write = []
    }
    merger = {
      read  = [ for idx in local.works_identified_list : idx.name ]
      write = concat(
        [ for idx in local.works_denormalised_list : idx.name ],
        [ for idx in local.images_initial_list : idx.name ]
      )
    }
    path_concatenator = {
      read  = [ for idx in local.works_denormalised_list : idx.name ]
      write = [ for idx in local.works_denormalised_list : idx.name ]
    }
    relation_embedder = {
      read  = [ for idx in local.works_denormalised_list : idx.name ]
      write = [ for idx in local.works_denormalised_list : idx.name ]
    }
    work_ingestor = {
      read  = [ for idx in local.works_denormalised_list : idx.name ]
      write = [ for idx in local.works_indexed_list : idx.name ]
    }
    inferrer = {
      read  = [ for idx in local.images_initial_list : idx.name ]
      write = [ for idx in local.images_augmented_list : idx.name ]
    }
    graph_extractor = {
      read  = [ for idx in local.works_denormalised_list : idx.name ]
      write = []
    }
    image_ingestor = {
      read  = [ for idx in local.images_augmented_list : idx.name ]
      write = [ for idx in local.images_indexed_list : idx.name ]
    }
    concept_ingestor = {
      read  = [ for idx in local.concepts_indexed_list : idx.name ]
      write = [ for idx in local.concepts_indexed_list : idx.name ]
    }
    snapshot_generator = {
      read  = concat(
        [ for idx in local.works_indexed_list : idx.name ],
        [ for idx in local.images_indexed_list : idx.name ]
      )
      write = []
    }
    catalogue_api = {
      read  = concat(
        [ for idx in local.works_indexed_list : idx.name ],
        [ for idx in local.images_indexed_list : idx.name ]
      )
      write = []
    }
    concepts_api = {
      read  = [ for idx in local.concepts_indexed_list : idx.name ]
      write = []
    }
  }

  pipeline_storage_es_service_secrets = {
    for service in keys(local.service_index_permissions) : service => {
      es_host     = local.pipeline_storage_private_host
      es_port     = local.pipeline_storage_port
      es_protocol = local.pipeline_storage_protocol
      es_apikey   = "elasticsearch/pipeline_storage_${var.pipeline_date}/${service}/api_key"
    }
  }

  catalogue_account_services = toset([
    "catalogue_api",
    "snapshot_generator",
    "concepts_api",
  ])
}


module "pipeline_services" {
  source    = "../pipeline_es_api_key"
  for_each  = local.service_index_permissions
  name      = each.key
  read_from = each.value.read
  write_to  = each.value.write

  pipeline_date       = var.pipeline_date
  expose_to_catalogue = contains(local.catalogue_account_services, each.key)
  providers = {
    aws.catalogue = aws.catalogue
  }
}

# This role isn't used by applications, but instead provided to give developer scripts
# read-only access to the pipeline_storage cluster.
resource "elasticstack_elasticsearch_security_role" "read_only" {
  name = "read_only"

  indices {
    names      = ["images*", "concepts*", "works*"]
    privileges = ["read"]
  }
}

resource "random_password" "read_only_user" {
  length = 16
}

resource "elasticstack_elasticsearch_security_user" "read_only" {
  username = "read_only"
  password = random_password.read_only_user.result
  roles    = [elasticstack_elasticsearch_security_role.read_only.name]
}

module "readonly_user_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.4.0"

  deletion_mode = "IMMEDIATE"
  key_value_map = {
    "elasticsearch/pipeline_storage_${var.pipeline_date}/read_only/es_username" = elasticstack_elasticsearch_security_user.read_only.username
    "elasticsearch/pipeline_storage_${var.pipeline_date}/read_only/es_password" = elasticstack_elasticsearch_security_user.read_only.password
  }
}
