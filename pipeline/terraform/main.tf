locals {
  stacks = {
    "2020-11-18" = {
      release_label = "prod"

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
    }
  }
}

module "stack" {
  source = "./stack"

  for_each = local.stacks

  pipeline_date = each.key
  release_label = each.value.release_label

  sierra_adapter_topic_arns = each.value.sierra_adapter_topic_arns
  miro_adapter_topic_arns   = each.value.miro_adapter_topic_arns
  mets_adapter_topic_arns   = each.value.mets_adapter_topic_arns
  calm_adapter_topic_arns   = each.value.calm_adapter_topic_arns

  # Boilerplate that shouldn't change between pipelines.

  account_id      = data.aws_caller_identity.current.account_id
  aws_region      = local.aws_region
  vpc_id          = local.vpc_id
  subnets         = local.private_subnets
  private_subnets = local.private_subnets

  dlq_alarm_arn = local.dlq_alarm_arn

  # RDS
  rds_ids_access_security_group_id = local.rds_access_security_group_id

  # Adapter VHS
  vhs_miro_read_policy              = local.vhs_miro_read_policy
  vhs_sierra_read_policy            = local.vhs_sierra_read_policy
  vhs_sierra_sourcedata_bucket_name = local.vhs_sierra_sourcedata_bucket_name
  vhs_sierra_sourcedata_table_name  = local.vhs_sierra_sourcedata_table_name
  mets_adapter_read_policy          = local.mets_adapter_read_policy
  mets_adapter_table_name           = local.mets_adapter_table_name
  vhs_calm_read_policy              = local.vhs_calm_read_policy
  vhs_calm_sourcedata_bucket_name   = local.vhs_calm_sourcedata_bucket_name
  vhs_calm_sourcedata_table_name    = local.vhs_calm_sourcedata_table_name
  read_storage_s3_role_arn          = aws_iam_role.read_storage_s3.arn

  # Inferrer data
  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging
  repository_urls        = data.aws_ecr_repository.service.*.repository_url
  services               = local.services
}
