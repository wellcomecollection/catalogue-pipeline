module "catalogue_pipeline_2021-01-12" {
  source = "./stack"

  pipeline_date = "2021-01-12"
  release_label = "prod"

  # Transformer config
  #
  # If this pipeline is meant to be reindexed, remember to uncomment the
  # reindexer topic names.

  sierra_adapter_topic_arns = [
    /*local.sierra_reindexer_topic_arn,*/
    local.sierra_merged_bibs_topic_arn,
    local.sierra_merged_items_topic_arn,
  ]

  miro_adapter_topic_arns = [
    /*local.miro_reindexer_topic_arn,*/
    local.miro_updates_topic_arn,
  ]

  mets_adapter_topic_arns = [
    /*local.mets_reindexer_topic_arn,*/
    local.mets_adapter_topic_arn,
  ]

  calm_adapter_topic_arns = [
    /*local.calm_reindexer_topic_arn,*/
    local.calm_adapter_topic_arn,
  ]

  # Boilerplate that shouldn't change between pipelines.

  aws_region = local.aws_region
  vpc_id     = local.vpc_id
  subnets    = local.private_subnets

  dlq_alarm_arn = local.dlq_alarm_arn

  # Security groups
  rds_ids_access_security_group_id   = local.rds_access_security_group_id
  pipeline_storage_security_group_id = local.pipeline_storage_security_group_id

  # Adapter VHS
  vhs_miro_read_policy   = local.vhs_miro_read_policy
  vhs_sierra_read_policy = local.vhs_sierra_read_policy
  vhs_calm_read_policy   = local.vhs_calm_read_policy

  # Inferrer data
  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  storage_bucket_name = local.storage_bucket
}

module "catalogue_pipeline_2021-01-19" {
  source = "./stack"

  pipeline_date = "2021-01-19"
  release_label = "stage"

  pipeline_storage_id = "pipeline_storage_delta"
  max_capacity        = 1

  # Transformer config
  #
  # If this pipeline is meant to be reindexed, remember to uncomment the
  # reindexer topic names.

  sierra_adapter_topic_arns = [
    local.sierra_reindexer_topic_arn,
    local.sierra_merged_bibs_topic_arn,
    local.sierra_merged_items_topic_arn,
  ]

  miro_adapter_topic_arns = [
    local.miro_reindexer_topic_arn,
    local.miro_updates_topic_arn,
  ]

  mets_adapter_topic_arns = [
    local.mets_reindexer_topic_arn,
    local.mets_adapter_topic_arn,
  ]

  calm_adapter_topic_arns = [
    local.calm_reindexer_topic_arn,
    local.calm_adapter_topic_arn,
  ]

  # Boilerplate that shouldn't change between pipelines.

  aws_region = local.aws_region
  vpc_id     = local.vpc_id
  subnets    = local.private_subnets

  dlq_alarm_arn = local.dlq_alarm_arn

  # Security groups
  rds_ids_access_security_group_id   = local.rds_access_security_group_id
  pipeline_storage_security_group_id = local.pipeline_storage_security_group_id

  # Adapter VHS
  vhs_miro_read_policy   = local.vhs_miro_read_policy
  vhs_sierra_read_policy = local.vhs_sierra_read_policy
  vhs_calm_read_policy   = local.vhs_calm_read_policy

  # Inferrer data
  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  storage_bucket_name = local.storage_bucket
}
