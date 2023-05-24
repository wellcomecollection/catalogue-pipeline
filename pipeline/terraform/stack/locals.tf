locals {
  namespace = "catalogue-${var.pipeline_date}"

  es_works_source_index       = "works-source-${var.pipeline_date}"
  es_works_merged_index       = "works-merged-${var.pipeline_date}"
  es_works_identified_index   = "works-identified-${var.pipeline_date}"
  es_works_denormalised_index = "works-denormalised-${var.pipeline_date}"
  es_works_index              = "works-indexed-${var.pipeline_date}"

  es_images_initial_index   = "images-initial-${var.pipeline_date}"
  es_images_augmented_index = "images-augmented-${var.pipeline_date}"
  es_images_index           = "images-indexed-${var.pipeline_date}"

  # Path to folder containing mappings and analysis settings for Elasticsearch Index creation
  es_config_path = "${path.root}/../../../index_config"

  # The max number of connections allowed by the instance.
  # specified at /infrastructure/critical/rds_id_minter.tf
  base_rds_instances             = 1
  id_minter_rds_max_connections  = (local.base_rds_instances + local.extra_rds_instances) * 45
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
    "router",
    "path_concatenator",
    "batcher",
    "relation_embedder",
    "transformer_miro",
    "transformer_mets",
    "transformer_tei",
    "transformer_sierra",
    "transformer_calm",
  ]

  fargate_service_boilerplate = {
    egress_security_group_id             = aws_security_group.egress.id
    elastic_cloud_vpce_security_group_id = var.network_config.ec_privatelink_security_group_id

    cluster_name = aws_ecs_cluster.cluster.name
    cluster_arn  = aws_ecs_cluster.cluster.id

    scale_down_adjustment = local.scale_down_adjustment
    scale_up_adjustment   = local.scale_up_adjustment

    dlq_alarm_topic_arn = var.monitoring_config.dlq_alarm_arn

    subnets = var.network_config.subnets

    namespace = local.namespace

    shared_logging_secrets = var.monitoring_config.shared_logging_secrets
  }
}
