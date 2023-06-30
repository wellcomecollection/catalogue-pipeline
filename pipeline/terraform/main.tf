locals {
  pipelines = {
    "2023-06-09" = {
      listen_to_reindexer      = false
      scale_up_tasks           = false
      scale_up_elastic_cluster = false
      scale_up_id_minter_db    = false
      scale_up_matcher_db      = false

      index_config = {
        works = {
          identified = "works_identified.2023-05-26"
          merged     = "works_merged.2023-05-26"
          indexed    = "works_indexed.2023-05-26"
        }
        images = {
          indexed        = "images_indexed.2023-05-26"
          works_analysis = "works_indexed.2023-05-26"
        }
      }
    },
    "2023-06-26" = {
      listen_to_reindexer      = false
      scale_up_tasks           = false
      scale_up_elastic_cluster = false
      scale_up_id_minter_db    = false
      scale_up_matcher_db      = false

      index_config = {
        works = {
          identified = "works_identified.2023-05-26"
          merged     = "works_merged.2023-05-26"
          indexed    = "works_indexed.2023-05-26"
        }
        images = {
          indexed        = "images_indexed.2023-06-26"
          works_analysis = "works_indexed.2023-05-26"
        }
      }
    }
  }
}

module "pipelines" {
  source = "./modules/stack"

  for_each = local.pipelines

  pipeline_date = each.key
  release_label = each.key

  reindexing_state = {
    listen_to_reindexer      = each.value["listen_to_reindexer"]
    scale_up_tasks           = each.value["scale_up_tasks"]
    scale_up_elastic_cluster = each.value["scale_up_elastic_cluster"]
    scale_up_id_minter_db    = each.value["scale_up_id_minter_db"]
    scale_up_matcher_db      = each.value["scale_up_matcher_db"]
  }

  index_config = each.value["index_config"]

  # Boilerplate that shouldn't change between pipelines.

  adapter_config    = local.adapter_config
  inferrer_config   = local.inferrer_config
  monitoring_config = local.monitoring_config
  network_config    = local.network_config
  rds_config        = local.rds_config

  providers = {
    aws.catalogue = aws.catalogue
  }
}
