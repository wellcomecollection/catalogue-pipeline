locals {
  pipelines = {
    "2023-06-01" = {
      listen_to_reindexer      = false
      scale_up_tasks           = false
      scale_up_elastic_cluster = false
      scale_up_id_minter_db    = false
      scale_up_matcher_db      = false
    }

    "2023-06-09" = {
      listen_to_reindexer      = true
      scale_up_tasks           = true
      scale_up_elastic_cluster = true
      scale_up_id_minter_db    = true
      scale_up_matcher_db      = true
    }
  }
}

module "pipelines" {
  source = "./stack"

  for_each = local.pipelines

  pipeline_date    = each.key
  release_label    = each.key
  reindexing_state = each.value

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
