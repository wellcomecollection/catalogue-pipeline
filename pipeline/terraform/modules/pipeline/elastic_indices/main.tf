locals {
  es_config_path = "${path.root}/../../../index_config"
  index_config_dates = [
    for date, cfg in var.index_config : {
      date     = date
      works    = try(cfg.works, {})
      images   = try(cfg.images, {})
      concepts = try(cfg.concepts, {})
    }
  ]
  works_source_list = [
    for cfg in local.index_config_dates : {
      name             = "works-source-${cfg.date}"
      default_pipeline = elasticstack_elasticsearch_ingest_pipeline.set_indexed_at.name
      mappings_name    = cfg.works.source
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
  for_each         = local.index_definitions
  source           = "../../es_index"
  name             = each.value.name
  mappings_name    = each.value.mappings_name
  config_path      = local.es_config_path
  allow_delete     = var.allow_delete_indices
  default_pipeline = try(each.value.default_pipeline, null)
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
    transformer_axiell = {
      read  = []
      write = ["works-source-2026-01-12"]
    }
    transformer_folio = {
      read  = []
      write = ["works-source-2026-01-12"]
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
    work_ingestor = {
      read  = [for idx in local.works_denormalised_list : idx.name]
      write = [for idx in local.works_indexed_list : idx.name]
    }
    inferrer = {
      read  = [for idx in local.images_initial_list : idx.name]
      write = [for idx in local.images_augmented_list : idx.name]
    }
    graph_extractor = {
      read = concat([
        for idx in local.works_denormalised_list : idx.name
        ], [
        for idx in local.images_augmented_list : idx.name
      ])
      write = []
    }
    image_ingestor = {
      read  = [for idx in local.images_augmented_list : idx.name]
      write = [for idx in local.images_indexed_list : idx.name]
    }
    concepts_ingestor = {
      read  = [for idx in local.works_denormalised_list : idx.name]
      write = [for idx in local.concepts_indexed_list : idx.name]
    }
    works_ingestor = {
      read  = [for idx in local.works_denormalised_list : idx.name]
      write = [for idx in local.works_indexed_list : idx.name]
    }
    images_ingestor = {
      read = concat([for idx in local.works_denormalised_list : idx.name], [
        for idx in local.images_augmented_list : idx.name
      ])
      write = [for idx in local.images_indexed_list : idx.name]
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
      es_host     = var.es_private_host
      es_port     = var.es_port
      es_protocol = var.es_protocol
      es_apikey   = "elasticsearch/pipeline_storage_${var.pipeline_date}/${service}/api_key"
    }
  }

  api_key_versions = {
    for k, v in local.service_index_permissions : k => module.pipeline_services[k].version
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

resource "elasticstack_elasticsearch_ingest_pipeline" "set_indexed_at" {
  name        = "set-indexed-at"
  description = "Sets indexed_at to the current timestamp on ingest"

  processors = [
    jsonencode({
      set = {
        field = "indexed_at"
        value = "{{_ingest.timestamp}}"
      }
    })
  ]
}
