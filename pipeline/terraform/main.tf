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
  network_config = local.network_config
  rds_config     = local.rds_config

  dlq_alarm_arn = local.dlq_alarm_arn

  # Adapter VHS
  vhs_miro_read_policy   = local.vhs_miro_read_policy
  vhs_sierra_read_policy = local.vhs_sierra_read_policy
  vhs_calm_read_policy   = local.vhs_calm_read_policy

  # Inferrer data
  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  tei_adapter_bucket_name = local.tei_adapter_bucket_name
  storage_bucket_name     = local.storage_bucket

  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  providers = {
    aws.catalogue = aws.catalogue
  }

  logging_cluster_id = local.logging_cluster_id
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
  network_config = local.network_config
  rds_config     = local.rds_config

  dlq_alarm_arn = local.dlq_alarm_arn

  # Adapter VHS
  vhs_miro_read_policy   = local.vhs_miro_read_policy
  vhs_sierra_read_policy = local.vhs_sierra_read_policy
  vhs_calm_read_policy   = local.vhs_calm_read_policy

  # Inferrer data
  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  tei_adapter_bucket_name = local.tei_adapter_bucket_name
  storage_bucket_name     = local.storage_bucket

  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  providers = {
    aws.catalogue = aws.catalogue
  }

  logging_cluster_id = local.logging_cluster_id
}
