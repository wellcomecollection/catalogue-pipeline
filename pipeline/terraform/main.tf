module "catalogue_pipeline_2022-07-26" {
  source = "./stack"

  pipeline_date = "2022-07-26"
  release_label = "2022-07-26"

  reindexing_state = {
    listen_to_reindexer      = false
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_id_minter_db    = false
    scale_up_matcher_db      = false
  }

  # This pipeline is disabled to avoid polluting the ID minter database
  # with label-derived IDs that aren't normalised.
  max_capacity = 0

  # Boilerplate that shouldn't change between pipelines.

  adapter_config = local.adapter_config
  logging_config = local.logging_config
  network_config = local.network_config
  rds_config     = local.rds_config

  dlq_alarm_arn = local.dlq_alarm_arn

  providers = {
    aws.catalogue = aws.catalogue
  }
}

module "catalogue_pipeline_2022-08-04" {
  source = "./stack"

  pipeline_date = "2022-08-04"
  release_label = "2022-08-04"

  reindexing_state = {
    listen_to_reindexer      = false
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_id_minter_db    = false
    scale_up_matcher_db      = false
  }

  # This pipeline is disabled to avoid polluting the ID minter database
  # with label-derived IDs that aren't normalised.
  max_capacity = 0

  # Boilerplate that shouldn't change between pipelines.

  adapter_config = local.adapter_config
  logging_config = local.logging_config
  network_config = local.network_config
  rds_config     = local.rds_config

  dlq_alarm_arn = local.dlq_alarm_arn

  providers = {
    aws.catalogue = aws.catalogue
  }
}
