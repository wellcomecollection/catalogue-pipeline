module "catalogue_pipeline_2022-08-24" {
  source = "./stack"

  pipeline_date = "2022-08-24"
  release_label = "2022-08-24"

  # The cluster in the 2022-08-24 pipeline is having issues, so we've
  # turned off all the services to avoid making the problem worse.
  #
  # Additionally, the changes to add label-derived IDs to contributors
  # may cause a flood of updates in the overnight harvest on the weekend.
  max_capacity = 0

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

module "catalogue_pipeline_2022-09-22" {
  source = "./stack"

  pipeline_date = "2022-09-22"
  release_label = "2022-09-22"

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

module "catalogue_pipeline_2022-09-23" {
  source = "./stack"

  pipeline_date = "2022-09-23"
  release_label = "2022-09-22"

  reindexing_state = {
    listen_to_reindexer      = true
    scale_up_tasks           = true
    scale_up_elastic_cluster = true
    scale_up_id_minter_db    = true
    scale_up_matcher_db      = true
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

