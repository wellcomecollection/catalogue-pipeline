module "router_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name      = "${var.namespace}_router-${local.tei_suffix}"
  topic_arns      = [module.merger_works_topic.arn]
  alarm_topic_arn = var.dlq_alarm_arn

  visibility_timeout_seconds = 60
}

module "router" {
  source          = "../../modules/service"

  namespace = var.namespace
  name      = "router-${local.tei_suffix}"

  container_image = var.router_image
  security_group_ids = [
    # TODO: Do we need the egress security group?
    var.service_egress_security_group_id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = var.cluster_name
  cluster_arn  = data.aws_ecs_cluster.cluster.id

  env_vars = {
    metrics_namespace = "${var.namespace}_router"

    queue_url         = module.router_queue.url
    queue_parallelism = 10

    paths_topic_arn = module.router_path_output_topic.arn
    works_topic_arn = module.router_work_output_topic.arn

    es_merged_index        = local.es_works_merged_index
    es_denormalised_index  = local.es_works_denormalised_index
    batch_size             = 100
    flush_interval_seconds = 30
  }

  secret_env_vars = var.pipeline_storage_es_service_secrets["router"]

  shared_logging_secrets = var.shared_logging_secrets

  subnets = var.subnets

  min_capacity = var.min_capacity
  max_capacity = min(10, var.max_capacity)

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment

  queue_read_policy = module.router_queue.read_policy

  cpu    = 1024
  memory = 2048

  use_fargate_spot = true

  deployment_service_env  = var.release_label
  deployment_service_name = "work-router-${local.tei_suffix}"
}

module "router_path_output_topic" {
  source = "../../modules/topic"

  name       = "${var.namespace}_router_path_output"
  role_names = [module.router.task_role_name]
}

module "router_work_output_topic" {
  source = "../../modules/topic"

  name       = "${var.namespace}_router_work_output-${local.tei_suffix}"
  role_names = [module.router.task_role_name]
}

module "router_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.router_queue.name

  queue_high_actions = [module.router.scale_up_arn]
  queue_low_actions  = [module.router.scale_down_arn]
}
