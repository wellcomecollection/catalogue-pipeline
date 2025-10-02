
// This file is used to create an empty zip file for Lambda functions that
// don't have any code yet. If we move to container images for all Lambdas
// we can probably delete this.
data "archive_file" "empty_zip" {
  output_path = "data/empty.zip"
  type        = "zip"
  source {
    content  = "// This file is intentionally left empty"
    filename = "lambda.py"
  }
}

locals {
  namespace = "catalogue-${var.pipeline_date}"

  es_works_source_index       = "works-source-${var.pipeline_date}"
  es_works_identified_index   = "works-identified-${var.pipeline_date}"
  es_works_denormalised_index = "works-denormalised-${var.pipeline_date}"
  es_works_index              = "works-indexed-${var.pipeline_date}"

  es_concepts_index_prefix = "concepts-indexed"

  es_images_initial_index   = "images-initial-${var.pipeline_date}"
  es_images_augmented_index = "images-augmented-${var.pipeline_date}"
  es_images_index           = "images-indexed-${var.pipeline_date}"

  # Path to folder containing mappings and analysis settings for Elasticsearch Index creation
  es_config_path = "${path.root}/../../../index_config"

  # The max number of connections allowed by the instance.
  # specified at /infrastructure/critical/rds_id_minter.tf
  id_minter_task_max_connections = min(9, local.max_capacity)

  # We don't want to overload our databases if we're not reindexing
  # and don't have extra database capacity provisioned.
  #
  # Note: during a reindex, we usually cap the number of ingestors:
  #
  #     = 6 * works ingestors + 5 * image ingestors
  #
  # These are ingestors writing into an empty index with no read traffic.
  # We want lots of parallelism to get through the reindex quickly.
  #
  # When we're not reindexing, our ingestors are writing into a full index
  # that may be serving API queries.  We want to avoid sending too many
  # queries to Elasticsearch and breaking the cluster.
  #
  # We also want to avoid running more ingestors when not reindexing
  # than when we are!
  max_capacity = var.reindexing_state.scale_up_tasks ? var.max_capacity : min(1, var.max_capacity)

  # If we're reindexing, our services will scale up to max capacity,
  # work through everything on the reindex queues, and then suddenly
  # finish processing everything -- at which point they all become idle.
  #
  # If we only stop one task per minute, that's a lot of tasks doing
  # nothing.  By increasing the scale_down_adjustment during reindexes,
  # we'll stop tasks faster and make reindexing cheaper.
  #
  # Note: if the scale down adjustment is greater than the number of tasks,
  # ECS will just stop every task.  e.g. if scale_down_adjustment = -5 and
  # there are 3 tasks running, ECS will scale the tasks down to zero.
  scale_down_adjustment = var.reindexing_state.scale_up_tasks ? -5 : -1
  scale_up_adjustment   = var.reindexing_state.scale_up_tasks ? 5 : 1

  services = [
    "ingestor_works",
    "ingestor_images",
    "matcher",
    "merger",
    "id_minter",
    "inference_manager",
    "feature_inferrer",
    "palette_inferrer",
    "aspect_ratio_inferrer",
    "path_concatenator",
    "batcher",
    "relation_embedder",
    "transformer_miro",
    "transformer_mets",
    "transformer_tei",
    "transformer_sierra",
    "transformer_calm",
    "transformer_ebsco",
  ]

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

  # EBSCO adapter topics
  ebsco_adapter_topic_arn = data.terraform_remote_state.ebsco_adapter.outputs.ebsco_adapter_topic_arn
  ebsco_adapter_bucket    = data.terraform_remote_state.ebsco_adapter.outputs.ebsco_adapter_bucket_name

  # Calm adapter VHS
  vhs_calm_read_policy = data.terraform_remote_state.calm_adapter.outputs.vhs_read_policy

  # Calm adapter topics
  calm_adapter_topic_arn   = data.terraform_remote_state.calm_adapter.outputs.calm_adapter_topic_arn
  calm_deletions_topic_arn = data.terraform_remote_state.calm_adapter.outputs.calm_deletions_topic_arn

  # Reindexer topics
  ebsco_reindexer_topic_arn  = data.terraform_remote_state.reindexer.outputs.ebsco_reindexer_topic_arn
  miro_reindexer_topic_arn   = data.terraform_remote_state.shared_infra.outputs.catalogue_miro_reindex_topic_arn
  sierra_reindexer_topic_arn = data.terraform_remote_state.shared_infra.outputs.catalogue_sierra_reindex_topic_arn
  mets_reindexer_topic_arn   = data.terraform_remote_state.reindexer.outputs.mets_reindexer_topic_arn
  tei_reindexer_topic_arn    = data.terraform_remote_state.reindexer.outputs.tei_reindexer_topic_arn
  calm_reindexer_topic_arn   = data.terraform_remote_state.reindexer.outputs.calm_reindexer_topic_arn

  infra_critical   = data.terraform_remote_state.catalogue_infra_critical.outputs
  shared_infra     = data.terraform_remote_state.shared_infra.outputs
  monitoring_infra = data.terraform_remote_state.monitoring.outputs

  adapter_config = {
    sierra = {
      topics = [
        local.sierra_merged_bibs_topic_arn,
        local.sierra_merged_items_topic_arn,
        local.sierra_merged_holdings_topic_arn,
        local.sierra_merged_orders_topic_arn,
      ]
      reindex_topic = local.sierra_reindexer_topic_arn
      read_policy   = local.vhs_sierra_read_policy,
    }

    miro = {
      topics = [
        local.miro_updates_topic_arn,
      ]
      reindex_topic = local.miro_reindexer_topic_arn,
      read_policy   = local.vhs_miro_read_policy
    }

    mets = {
      topics = [
        local.mets_adapter_topic_arn,
      ],
      reindex_topic = local.mets_reindexer_topic_arn,
      read_policy   = data.aws_iam_policy_document.read_storage_bucket.json
    }

    calm = {
      topics = [
        local.calm_adapter_topic_arn,
        local.calm_deletions_topic_arn,
      ],
      reindex_topic = local.calm_reindexer_topic_arn,
      read_policy   = local.vhs_calm_read_policy
    }

    tei = {
      topics = [
        local.tei_adapter_topic_arn,
      ],
      reindex_topic = local.tei_reindexer_topic_arn,
      read_policy   = data.aws_iam_policy_document.read_tei_adapter_bucket.json
    }

    ebsco = {
      topics = [
        local.ebsco_adapter_topic_arn,
      ],
      reindex_topic = local.ebsco_reindexer_topic_arn
      read_policy   = data.aws_iam_policy_document.read_ebsco_adapter_bucket.json
    }
  }

  monitoring_config = {
    shared_logging_secrets       = local.shared_infra["shared_secrets_logging"]
    logging_cluster_id           = local.shared_infra["logging_cluster_id"]
    dlq_alarm_arn                = null
    main_q_age_alarm_action_arns = [local.monitoring_infra["chatbot_topic_arn"]]
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
    subnet_group      = local.infra_critical.rds_subnet_group_name
    security_group_id = local.infra_critical.rds_access_security_group_id
  }

  lambda_vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  fargate_service_boilerplate = {
    egress_security_group_id             = aws_security_group.egress.id
    elastic_cloud_vpce_security_group_id = local.network_config.ec_privatelink_security_group_id

    cluster_name = aws_ecs_cluster.cluster.name
    cluster_arn  = aws_ecs_cluster.cluster.id

    scale_down_adjustment = local.scale_down_adjustment
    scale_up_adjustment   = local.scale_up_adjustment

    dlq_alarm_topic_arn          = local.monitoring_config.dlq_alarm_arn
    main_q_age_alarm_action_arns = local.monitoring_config.main_q_age_alarm_action_arns

    subnets = local.network_config.subnets

    namespace = local.namespace

    shared_logging_secrets = local.monitoring_config.shared_logging_secrets
  }
}
