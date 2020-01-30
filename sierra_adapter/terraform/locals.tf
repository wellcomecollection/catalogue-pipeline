locals {
  lambda_error_alarm_arn = data.terraform_remote_state.shared_infra.outputs.lambda_error_alarm_arn

  dlq_alarm_arn = data.terraform_remote_state.shared_infra.outputs.dlq_alarm_arn

  vpc_id          = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_delta_id
  private_subnets = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_delta_private_subnets

  vhs_full_access_policy = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_full_access_policy
  vhs_table_name         = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_table_name
  vhs_bucket_name        = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_bucket_name

  reindexed_items_topic_name = data.terraform_remote_state.shared_infra.outputs.catalogue_sierra_items_reindex_topic_name

  vhs_sierra_items_full_access_policy = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_items_full_access_policy
  vhs_sierra_items_table_name         = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_items_table_name
  vhs_sierra_items_bucket_name        = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_sierra_items_bucket_name
}
