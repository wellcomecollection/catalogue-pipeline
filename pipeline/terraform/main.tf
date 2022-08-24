module "catalogue_pipeline_2022-08-08" {
  source = "./stack"

  pipeline_date = "2022-08-08"
  release_label = "2022-08-08"

  reindexing_state = {
    listen_to_reindexer      = false
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_id_minter_db    = false
    scale_up_matcher_db      = false
  }

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

module "catalogue_pipeline_2022-08-24" {
  source = "./stack"

  pipeline_date = "2022-08-24"
  release_label = "2022-08-24"

  reindexing_state = {
    listen_to_reindexer      = false
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_id_minter_db    = false
    scale_up_matcher_db      = false
  }

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

