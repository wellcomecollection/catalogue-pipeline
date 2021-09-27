module "catalogue_pipeline_2021-08-16" {
  source = "./stack"

  pipeline_date = "2021-08-16"
  release_label = "2021-08-16"

  is_reindexing = false

  # Boilerplate that shouldn't change between pipelines.

  adapters = {
    sierra = {
      topics = [
        local.sierra_merged_bibs_topic_arn,
        local.sierra_merged_items_topic_arn,
        local.sierra_merged_holdings_topic_arn,
      ]
      reindex_topic = local.sierra_reindexer_topic_arn
    }

    miro = {
      topics = [
        local.miro_updates_topic_arn,
      ]
      reindex_topic = local.miro_reindexer_topic_arn,
    }

    mets = {
      topics = [
        local.mets_adapter_topic_arn,
      ],
      reindex_topic = local.mets_reindexer_topic_arn,
    }

    calm = {
      topics = [
        local.calm_adapter_topic_arn,
        local.calm_deletions_topic_arn,
      ],
      reindex_topic = local.calm_reindexer_topic_arn,
    }
    # we want to keep tei out of the prod pipeline for now,
    # while me make sure that the data is good enough to present
    # and while we extend the transformer to extract more of it.
    #
    # Note: we have to supply a real topic ARN here, not just 'null',
    # or the module gets confused.  For now we just create a dummy topic
    # that will never receive messages.
    tei = {
      topics = [
        local.tei_adapter_topic_arn,
      ],
      reindex_topic = local.tei_reindexer_topic_arn,
    }
  }

  vpc_id  = local.vpc_id
  subnets = local.private_subnets

  dlq_alarm_arn = local.dlq_alarm_arn

  rds_cluster_id        = local.rds_cluster_id
  rds_subnet_group_name = local.rds_subnet_group_name

  # Security groups
  rds_ids_access_security_group_id = local.rds_access_security_group_id
  ec_privatelink_security_group_id = local.ec_platform_privatelink_security_group_id

  traffic_filter_platform_vpce_id   = local.traffic_filter_platform_vpce_id
  traffic_filter_catalogue_vpce_id  = local.traffic_filter_catalogue_vpce_id
  traffic_filter_public_internet_id = local.traffic_filter_public_internet_id

  # Adapter VHS
  vhs_miro_read_policy   = local.vhs_miro_read_policy
  vhs_sierra_read_policy = local.vhs_sierra_read_policy
  vhs_calm_read_policy   = local.vhs_calm_read_policy

  # Inferrer data
  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  tei_adapter_bucket_name = local.tei_adapter_bucket_name

  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  storage_bucket_name = local.storage_bucket

  api_ec_version = local.api_ec_version

  providers = {
    aws.catalogue = aws.catalogue
  }

  logging_cluster_id = local.logging_cluster_id
}

module "catalogue_pipeline_2021-09-23" {
  source = "./stack"

  pipeline_date = "2021-09-23"
  release_label = "2021-09-23"

  is_reindexing = true

  # Boilerplate that shouldn't change between pipelines.

  adapters = {
    sierra = {
      topics = [
        local.sierra_merged_bibs_topic_arn,
        local.sierra_merged_items_topic_arn,
        local.sierra_merged_holdings_topic_arn,
      ]
      reindex_topic = local.sierra_reindexer_topic_arn
    }

    miro = {
      topics = [
        local.miro_updates_topic_arn,
      ]
      reindex_topic = local.miro_reindexer_topic_arn,
    }

    mets = {
      topics = [
        local.mets_adapter_topic_arn,
      ],
      reindex_topic = local.mets_reindexer_topic_arn,
    }

    calm = {
      topics = [
        local.calm_adapter_topic_arn,
        local.calm_deletions_topic_arn,
      ],
      reindex_topic = local.calm_reindexer_topic_arn,
    }
    # we want to keep tei out of the prod pipeline for now,
    # while me make sure that the data is good enough to present
    # and while we extend the transformer to extract more of it.
    #
    # Note: we have to supply a real topic ARN here, not just 'null',
    # or the module gets confused.  For now we just create a dummy topic
    # that will never receive messages.
    tei = {
      topics = [
        local.tei_adapter_topic_arn,
      ],
      reindex_topic = local.tei_reindexer_topic_arn,
    }
  }

  vpc_id  = local.vpc_id
  subnets = local.private_subnets

  dlq_alarm_arn = local.dlq_alarm_arn

  rds_cluster_id        = local.rds_cluster_id
  rds_subnet_group_name = local.rds_subnet_group_name

  # Security groups
  rds_ids_access_security_group_id = local.rds_access_security_group_id
  ec_privatelink_security_group_id = local.ec_platform_privatelink_security_group_id

  traffic_filter_platform_vpce_id   = local.traffic_filter_platform_vpce_id
  traffic_filter_catalogue_vpce_id  = local.traffic_filter_catalogue_vpce_id
  traffic_filter_public_internet_id = local.traffic_filter_public_internet_id

  # Adapter VHS
  vhs_miro_read_policy   = local.vhs_miro_read_policy
  vhs_sierra_read_policy = local.vhs_sierra_read_policy
  vhs_calm_read_policy   = local.vhs_calm_read_policy

  # Inferrer data
  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id

  tei_adapter_bucket_name = local.tei_adapter_bucket_name

  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

  storage_bucket_name = local.storage_bucket

  api_ec_version = local.api_ec_version

  providers = {
    aws.catalogue = aws.catalogue
  }

  logging_cluster_id = local.logging_cluster_id
}
