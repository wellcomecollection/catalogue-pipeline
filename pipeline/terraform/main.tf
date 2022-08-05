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

  # Boilerplate that shouldn't change between pipelines.

  adapter_config = local.adapter_config
  logging_config = local.logging_config
  network_config = local.network_config
  rds_config     = local.rds_config

  dlq_alarm_arn = local.dlq_alarm_arn

  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  providers = {
    aws.catalogue = aws.catalogue
  }
}

module "catalogue_pipeline_2022-08-04" {
  source = "./stack"

  pipeline_date = "2022-08-04"
  release_label = "2022-08-04"

  reindexing_state = {
    listen_to_reindexer      = true
    scale_up_tasks           = true
    scale_up_elastic_cluster = true
    scale_up_id_minter_db    = true
    scale_up_matcher_db      = true
  }

  # Boilerplate that shouldn't change between pipelines.

  adapter_config = local.adapter_config
  logging_config = local.logging_config
  network_config = local.network_config
  rds_config     = local.rds_config

  dlq_alarm_arn = local.dlq_alarm_arn

  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  providers = {
    aws.catalogue = aws.catalogue
  }
}
