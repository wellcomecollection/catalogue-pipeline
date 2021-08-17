locals {
  miro_updates_topic_arn = data.terraform_remote_state.shared_infra.outputs.miro_updates_topic_arn
  vhs_miro_read_policy   = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_miro_read_policy
  storage_bucket         = "wellcomecollection-storage"

  # Sierra adapter VHS
  vhs_sierra_read_policy = data.terraform_remote_state.sierra_adapter.outputs.vhs_read_policy

  # Sierra adapter topics
  sierra_merged_items_topic_arn    = data.terraform_remote_state.sierra_adapter.outputs.merged_items_topic_arn
  sierra_merged_bibs_topic_arn     = data.terraform_remote_state.sierra_adapter.outputs.merged_bibs_topic_arn
  sierra_merged_holdings_topic_arn = data.terraform_remote_state.sierra_adapter.outputs.merged_holdings_topic_arn

  # Mets adapter VHS
  mets_adapter_read_policy = data.terraform_remote_state.mets_adapter.outputs.mets_dynamo_read_policy

  # Mets adapter topics
  mets_adapter_topic_arn = data.terraform_remote_state.mets_adapter.outputs.mets_adapter_topic_arn

  # Tei adapter topics
  tei_adapter_topic_arn   = data.terraform_remote_state.tei_adapter.outputs.tei_adapter_topic_arn
  tei_adapter_bucket_name = data.terraform_remote_state.tei_adapter.outputs.tei_adapter_bucket_name

  # Calm adapter VHS
  vhs_calm_read_policy = data.terraform_remote_state.calm_adapter.outputs.vhs_read_policy

  # Calm adapter topics
  calm_adapter_topic_arn   = data.terraform_remote_state.calm_adapter.outputs.calm_adapter_topic_arn
  calm_deletions_topic_arn = data.terraform_remote_state.calm_adapter.outputs.calm_deletions_topic_arn

  # Reindexer topics
  miro_reindexer_topic_arn   = data.terraform_remote_state.shared_infra.outputs.catalogue_miro_reindex_topic_arn
  sierra_reindexer_topic_arn = data.terraform_remote_state.shared_infra.outputs.catalogue_sierra_reindex_topic_arn
  mets_reindexer_topic_arn   = data.terraform_remote_state.reindexer.outputs.mets_reindexer_topic_arn
  tei_reindexer_topic_arn    = data.terraform_remote_state.reindexer.outputs.tei_reindexer_topic_arn
  calm_reindexer_topic_arn   = data.terraform_remote_state.reindexer.outputs.calm_reindexer_topic_arn

  # Infra stuff
  dlq_alarm_arn   = data.terraform_remote_state.monitoring.outputs.platform_dlq_alarm_topic_arn
  vpc_id          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]

  infra_critical = data.terraform_remote_state.catalogue_infra_critical.outputs

  rds_access_security_group_id = local.infra_critical.rds_access_security_group_id
  rds_cluster_id               = local.infra_critical.rds_cluster_id
  rds_subnet_group_name        = local.infra_critical.rds_subnet_group_name

  shared_infra = data.terraform_remote_state.shared_infra.outputs

  ec_platform_privatelink_security_group_id = local.shared_infra["ec_platform_privatelink_sg_id"]

  traffic_filter_platform_vpce_id   = local.shared_infra["ec_platform_privatelink_traffic_filter_id"]
  traffic_filter_catalogue_vpce_id  = local.shared_infra["ec_catalogue_privatelink_traffic_filter_id"]
  traffic_filter_public_internet_id = local.shared_infra["ec_public_internet_traffic_filter_id"]

  logging_cluster_id = data.terraform_remote_state.shared_infra.outputs.logging_cluster_id
}
