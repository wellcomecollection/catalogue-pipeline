module "catalogue_pipeline_20200211" {
  source = "./stack"

  namespace = "catalogue-20200211"

  release_label = "prod"

  account_id      = data.aws_caller_identity.current.account_id
  aws_region      = local.aws_region
  vpc_id          = local.vpc_id
  subnets         = local.private_subnets
  private_subnets = local.private_subnets

  dlq_alarm_arn = local.dlq_alarm_arn

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

  # Elasticsearch
  es_works_index = "v2-20200131"

  # RDS
  rds_ids_access_security_group_id = local.rds_access_security_group_id

  # Adapter VHS
  vhs_sierra_read_policy            = local.vhs_sierra_read_policy
  vhs_miro_read_policy              = local.vhs_miro_read_policy
  vhs_sierra_sourcedata_bucket_name = local.vhs_sierra_sourcedata_bucket_name
  vhs_sierra_sourcedata_table_name  = local.vhs_sierra_sourcedata_table_name
  mets_adapter_read_policy          = local.mets_adapter_read_policy
  mets_adapter_table_name           = local.mets_adapter_table_name
  read_storage_s3_role_arn          = aws_iam_role.read_storage_s3.arn
}
