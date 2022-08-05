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
  sierra_merged_orders_topic_arn   = data.terraform_remote_state.sierra_adapter.outputs.merged_orders_topic_arn

  # Mets adapter VHS
  mets_adapter_read_policy = data.terraform_remote_state.mets_adapter.outputs.mets_dynamo_read_policy

  # Mets adapter topics
  mets_adapter_topic_arn = data.terraform_remote_state.mets_adapter.outputs.mets_adapter_topic_arn

  # Tei adapter topics
  tei_adapter_topic_arn = data.terraform_remote_state.tei_adapter.outputs.tei_adapter_topic_arn
  tei_adapter_bucket    = data.terraform_remote_state.tei_adapter.outputs.tei_adapter_bucket_name

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

  infra_critical = data.terraform_remote_state.catalogue_infra_critical.outputs

  rds_access_security_group_id = local.infra_critical.rds_access_security_group_id
  rds_cluster_id               = local.infra_critical.rds_cluster_id
  rds_subnet_group_name        = local.infra_critical.rds_subnet_group_name

  shared_infra = data.terraform_remote_state.shared_infra.outputs

  adapter_config = {
    sierra = {
      topics = [
        local.sierra_merged_bibs_topic_arn,
        local.sierra_merged_items_topic_arn,
        local.sierra_merged_holdings_topic_arn,
        local.sierra_merged_orders_topic_arn,
      ]
      reindex_topic = local.sierra_reindexer_topic_arn
      read_policy = local.vhs_sierra_read_policy,
    }

    miro = {
      topics = [
        local.miro_updates_topic_arn,
      ]
      reindex_topic = local.miro_reindexer_topic_arn,
      read_policy = local.vhs_miro_read_policy
    }

    mets = {
      topics = [
        local.mets_adapter_topic_arn,
      ],
      reindex_topic = local.mets_reindexer_topic_arn,
      read_policy = data.aws_iam_policy_document.read_storage_bucket.json
    }

    calm = {
      topics = [
        local.calm_adapter_topic_arn,
        local.calm_deletions_topic_arn,
      ],
      reindex_topic = local.calm_reindexer_topic_arn,
      read_policy = local.vhs_calm_read_policy
    }

    tei = {
      topics = [
        local.tei_adapter_topic_arn,
      ],
      reindex_topic = local.tei_reindexer_topic_arn,
      read_policy = data.aws_iam_policy_document.read_tei_adapter_bucket.json
    }
  }

  logging_config = {
    shared_secrets     = local.shared_infra["shared_secrets_logging"]
    logging_cluster_id = local.shared_infra["logging_cluster_id"]
  }

  network_config = {
    vpc_id  = local.catalogue_vpcs["catalogue_vpc_delta_id"]
    subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]

    ec_privatelink_security_group_id = local.shared_infra["ec_platform_privatelink_sg_id"]

    traffic_filters = [
      local.shared_infra["ec_platform_privatelink_traffic_filter_id"],
      local.shared_infra["ec_catalogue_privatelink_traffic_filter_id"],
      local.shared_infra["ec_public_internet_traffic_filter_id"],
    ]
  }

  rds_config = {
    cluster_id        = local.rds_cluster_id
    subnet_group      = local.rds_subnet_group_name
    security_group_id = local.rds_access_security_group_id
  }
}
