locals {
  miro_updates_topic_arn = data.terraform_remote_state.shared_infra.outputs.miro_updates_topic_arn
  vhs_miro_read_policy   = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_miro_read_policy
  storage_bucket         = "wellcomecollection-storage"

  # Sierra adapter VHS
  vhs_sierra_read_policy            = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_read_policy
  vhs_sierra_sourcedata_bucket_name = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_bucket_name
  vhs_sierra_sourcedata_table_name  = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_table_name

  # Sierra adapter topics
  sierra_merged_items_topic_arn = data.terraform_remote_state.sierra_adapter.outputs.merged_items_topic_arn
  sierra_merged_bibs_topic_arn  = data.terraform_remote_state.sierra_adapter.outputs.merged_bibs_topic_arn

  # Mets adapter VHS
  mets_adapter_read_policy = data.terraform_remote_state.catalogue_infra_critical.outputs.mets_dynamo_read_policy
  mets_adapter_table_name  = data.terraform_remote_state.catalogue_infra_critical.outputs.mets_dynamo_table_name

  # Mets adapter topics
  mets_adapter_topic_arn = data.terraform_remote_state.mets_adapter.outputs.mets_adapter_topic_arn

  # Calm adapter VHS
  vhs_calm_read_policy            = data.terraform_remote_state.calm_adapter.outputs.vhs_read_policy
  vhs_calm_sourcedata_bucket_name = data.terraform_remote_state.calm_adapter.outputs.vhs_bucket_name
  vhs_calm_sourcedata_table_name  = data.terraform_remote_state.calm_adapter.outputs.vhs_table_name

  # Calm adapter topics
  calm_adapter_topic_arn = data.terraform_remote_state.calm_adapter.outputs.calm_adapter_topic_arn

  # Reindexer topics
  miro_reindexer_topic_arn   = data.terraform_remote_state.shared_infra.outputs.catalogue_miro_reindex_topic_arn
  sierra_reindexer_topic_arn = data.terraform_remote_state.shared_infra.outputs.catalogue_sierra_reindex_topic_arn
  mets_reindexer_topic_arn   = data.terraform_remote_state.reindexer.outputs.mets_reindexer_topic_arn
  calm_reindexer_topic_arn   = data.terraform_remote_state.reindexer.outputs.calm_reindexer_topic_arn

  # Infra stuff
  infra_bucket                 = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  aws_region                   = "eu-west-1"
  dlq_alarm_arn                = data.terraform_remote_state.shared_infra.outputs.dlq_alarm_arn
  vpc_id                       = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_delta_id
  private_subnets              = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_delta_private_subnets
  rds_access_security_group_id = data.terraform_remote_state.catalogue_infra_critical.outputs.rds_access_security_group_id
}
