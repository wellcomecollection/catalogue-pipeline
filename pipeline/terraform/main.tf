module "catalogue_pipeline_2022-08-24" {
  source = "./stack"

  pipeline_date = "2022-08-24"
  release_label = "2022-08-24"

  # This pipeline is running Elasticsearch 8.4.2, which seems to be
  # broken with the queries we use.  I don't want to delete it without
  # double checking (in case it's useful for further investigation),
  # but we don't need to be running anything through it.
  max_capacity    = 0
  es_cluster_size = "2x2"

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

  # This pipeline is running Elasticsearch 8.4.2, which seems to be
  # broken with the queries we use.  I don't want to delete it without
  # double checking (in case it's useful for further investigation),
  # but we don't need to be running anything through it.
  max_capacity    = 0
  es_cluster_size = "2x2"

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
  release_label = "2022-09-23"

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

