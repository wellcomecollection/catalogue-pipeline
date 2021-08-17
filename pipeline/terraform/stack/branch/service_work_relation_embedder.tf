module "relation_embedder_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name      = "${local.namespace}_relation_embedder"
  topic_arns      = [module.batcher_output_topic.arn]
  alarm_topic_arn = var.dlq_alarm_arn

  # We know that 10 minutes is too short; some big archives can't be
  # processed in that time, and they end up on a DLQ.
  visibility_timeout_seconds = 30 * 60
}

module "relation_embedder" {
  source          = "../../modules/service"
  service_name    = "${local.namespace}_relation_embedder"
  container_image = var.relation_embedder_image

  security_group_ids = [
    # TODO: Do we need the egress security group?
    var.service_egress_security_group_id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = var.cluster_name
  cluster_arn  = data.aws_ecs_cluster.cluster.id

  env_vars = {
    metrics_namespace = "${local.namespace}_relation_embedder"

    queue_url = module.relation_embedder_queue.url
    topic_arn = module.relation_embedder_output_topic.arn

    es_merged_index       = local.es_works_merged_index
    es_denormalised_index = local.es_works_denormalised_index

    queue_parallelism            = 3  // NOTE: limit to avoid memory errors
    affected_works_scroll_size   = 50 // NOTE: limit to avoid memory errors
    complete_tree_scroll_size    = 800
    index_batch_size             = 100 // NOTE: too large results in 413 from ES
    index_flush_interval_seconds = 60
  }

  secret_env_vars = var.pipeline_storage_es_service_secrets["relation_embedder"]

  # NOTE: limit to avoid >500 concurrent scroll contexts
  min_capacity = var.min_capacity
  max_capacity = min(10, var.max_capacity)

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment

  subnets           = var.subnets
  queue_read_policy = module.relation_embedder_queue.read_policy

  cpu    = 2048
  memory = 4096

  use_fargate_spot = true

  depends_on = [
    var.elasticsearch_users,
  ]

  deployment_service_env  = var.release_label
  deployment_service_name = "work-relation-embedder-${local.tei}"
  shared_logging_secrets  = var.shared_logging_secrets
}

# Output topic

module "relation_embedder_output_topic" {
  source = "../../modules/topic"

  name       = "${local.namespace}_relation_embedder_output"
  role_names = [module.relation_embedder.task_role_name]
}

module "relation_embedder_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.relation_embedder_queue.name

  queue_high_actions = [module.relation_embedder.scale_up_arn]
  queue_low_actions  = [module.relation_embedder.scale_down_arn]
}
