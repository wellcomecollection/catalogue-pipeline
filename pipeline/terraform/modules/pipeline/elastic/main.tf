data "ec_stack" "latest_patch" {
  version_regex = var.version_regex
  region        = "eu-west-1"
}

resource "ec_deployment" "pipeline" {
  name                   = "pipeline-${var.pipeline_date}"
  version                = data.ec_stack.latest_patch.version
  region                 = data.ec_stack.latest_patch.region
  deployment_template_id = var.es_cluster_deployment_template
  traffic_filter         = var.network_config.traffic_filters

  elasticsearch {
    topology {
      id         = "hot_content"
      zone_count = var.es_node_count
      size       = var.es_memory
    }
  }
  kibana {
    topology {
      zone_count = 1
      size       = "1g"
    }
  }
  observability { deployment_id = var.monitoring_config.logging_cluster_id }
  lifecycle { ignore_changes = [version] }
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

  secrets_kv_map = {
    (local.pipeline_storage_public_host)  = "${local.pipeline_storage_elastic_id}.${local.pipeline_storage_elastic_region}.aws.found.io"
    (local.pipeline_storage_es_username)  = ec_deployment.pipeline.elasticsearch_username
    (local.pipeline_storage_es_password)  = ec_deployment.pipeline.elasticsearch_password
    (local.pipeline_storage_protocol)     = split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint)[0]
    (local.pipeline_storage_port)         = reverse(split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint))[0]
    (local.pipeline_storage_private_host) = "${local.pipeline_storage_elastic_id}.vpce.${local.pipeline_storage_elastic_region}.aws.elastic-cloud.com"
  }
}

module "pipeline_storage_secrets" {
  source        = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.3.0"
  deletion_mode = "IMMEDIATE"
  key_value_map = local.secrets_kv_map
}

module "pipeline_storage_secrets_catalogue" {
  source        = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.3.0"
<<<<<<< HEAD
  providers     = { aws = aws.catalogue }
=======
  providers     = { aws = aws }
>>>>>>> 003b066ed (elastic module)
  deletion_mode = "IMMEDIATE"
  key_value_map = local.secrets_kv_map
}

locals {
  es_config_path = "${path.root}/../../../index_config"
  index_config_dates = [for date, cfg in var.index_config : {
    date     = date
    works    = try(cfg.works, {})
    images   = try(cfg.images, {})
    concepts = try(cfg.concepts, {})
  }]
  works_source_list = [
    for cfg in local.index_config_dates : {
      name          = "works-source-${cfg.date}"
      mappings_name = cfg.works.source
    } if try(cfg.works.source, null) != null && cfg.works.source != ""
  ]
  works_identified_list = [
    for cfg in local.index_config_dates : {
      name          = "works-identified-${cfg.date}"
      mappings_name = cfg.works.identified
    } if try(cfg.works.identified, null) != null && cfg.works.identified != ""
  ]
  works_denormalised_list = [
    for cfg in local.index_config_dates : {
      name          = "works-denormalised-${cfg.date}"
      mappings_name = cfg.works.denormalised
    } if try(cfg.works.denormalised, null) != null && cfg.works.denormalised != ""
  ]
  works_indexed_list = [
    for cfg in local.index_config_dates : {
      name          = "works-indexed-${cfg.date}"
      mappings_name = cfg.works.indexed
    } if try(cfg.works.indexed, null) != null && cfg.works.indexed != ""
  ]
  images_initial_list = [
    for cfg in local.index_config_dates : {
      name          = "images-initial-${cfg.date}"
      mappings_name = cfg.images.initial
    } if try(cfg.images.initial, null) != null && cfg.images.initial != ""
  ]
  images_augmented_list = [
    for cfg in local.index_config_dates : {
      name          = "images-augmented-${cfg.date}"
      mappings_name = cfg.images.augmented
    } if try(cfg.images.augmented, null) != null && cfg.images.augmented != ""
  ]
  images_indexed_list = [
    for cfg in local.index_config_dates : {
      name          = "images-indexed-${cfg.date}"
      mappings_name = cfg.images.indexed
    } if try(cfg.images.indexed, null) != null && cfg.images.indexed != ""
  ]
  concepts_indexed_list = [
    for cfg in local.index_config_dates : {
      name          = "concepts-indexed-${cfg.date}"
      mappings_name = cfg.concepts.indexed
    } if try(cfg.concepts.indexed, null) != null && cfg.concepts.indexed != ""
  ]
  index_list        = concat(local.works_source_list, local.works_denormalised_list, local.works_identified_list, local.works_indexed_list, local.images_initial_list, local.images_augmented_list, local.images_indexed_list, local.concepts_indexed_list)
  index_definitions = { for i in local.index_list : i.name => i }
}

module "indices" {
  for_each      = local.index_definitions
  source        = "../../es_index"
  name          = each.value.name
  mappings_name = each.value.mappings_name
  config_path   = local.es_config_path
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
      write = [for idx in local.works_source_list : idx.name]
    }
    id_minter = {
      read  = [for idx in local.works_source_list : idx.name]
      write = [for idx in local.works_identified_list : idx.name]
    }
    matcher = {
      read  = [for idx in local.works_identified_list : idx.name]
      write = []
    }
    merger = {
      read = [for idx in local.works_identified_list : idx.name]
      write = concat([
        for idx in local.works_denormalised_list : idx.name
        ], [
        for idx in local.images_initial_list : idx.name
      ])
    }
    path_concatenator = {
      read  = [for idx in local.works_denormalised_list : idx.name]
      write = [for idx in local.works_denormalised_list : idx.name]
    }
    relation_embedder = {
      read  = [for idx in local.works_denormalised_list : idx.name]
      write = [for idx in local.works_denormalised_list : idx.name]
    }
    # TODO: Remove `work_ingestor` once we switch to new Python ingestor service
    work_ingestor = {
      read  = [for idx in local.works_denormalised_list : idx.name]
      write = [for idx in local.works_indexed_list : idx.name]
    }
    inferrer = {
      read  = [for idx in local.images_initial_list : idx.name]
      write = [for idx in local.images_augmented_list : idx.name]
    }
    graph_extractor = {
      read  = [for idx in local.works_denormalised_list : idx.name]
      write = []
    }
    image_ingestor = {
      read  = [for idx in local.images_augmented_list : idx.name]
      write = [for idx in local.images_indexed_list : idx.name]
    }
    # TODO: Remove `concept_ingestor` once we deploy incremental mode
    concept_ingestor = {
      read  = [for idx in local.concepts_indexed_list : idx.name]
      write = [for idx in local.concepts_indexed_list : idx.name]
    }
    concepts_ingestor = {
      read  = [for idx in local.works_denormalised_list : idx.name]
      write = [for idx in local.concepts_indexed_list : idx.name]
    }
    works_ingestor = {
      read = [for idx in local.works_denormalised_list : idx.name]
      # For now only allow writing to a non-production index for safety
      write = ["works-indexed-2025-10-09"]
    }
    snapshot_generator = {
      read = concat([
        for idx in local.works_indexed_list : idx.name
        ], [
        for idx in local.images_indexed_list : idx.name
      ])
      write = []
    }
    catalogue_api = {
      read = concat([
        for idx in local.works_indexed_list : idx.name
        ], [
        for idx in local.images_indexed_list : idx.name
      ])
      write = []
    }
    concepts_api = {
      read  = [for idx in local.concepts_indexed_list : idx.name]
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
}

module "pipeline_services" {
  for_each            = local.service_index_permissions
  source              = "../../pipeline_es_api_key"
  name                = each.key
  read_from           = each.value.read
  write_to            = each.value.write
  pipeline_date       = var.pipeline_date
  expose_to_catalogue = contains(var.catalogue_account_services, each.key)
  providers = {
    aws.catalogue = aws.catalogue
  }
}

resource "elasticstack_elasticsearch_security_role" "read_only" {
  name = "read_only"

  indices {
    names      = ["images*", "concepts*", "works*"]
    privileges = ["read"]
  }
}
resource "random_password" "read_only_user" { length = 16 }
resource "elasticstack_elasticsearch_security_user" "read_only" {
  username = "read_only"
  password = random_password.read_only_user.result
  roles    = [elasticstack_elasticsearch_security_role.read_only.name]
}
module "readonly_user_secrets" {
  source        = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.4.0"
  deletion_mode = "IMMEDIATE"
  key_value_map = {
    "elasticsearch/pipeline_storage_${var.pipeline_date}/read_only/es_username" = elasticstack_elasticsearch_security_user.read_only.username
    "elasticsearch/pipeline_storage_${var.pipeline_date}/read_only/es_password" = elasticstack_elasticsearch_security_user.read_only.password
  }
}
