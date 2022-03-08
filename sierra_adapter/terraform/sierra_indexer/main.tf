locals {
  service_name = "${var.namespace}-sierra_indexer"
}

module "service" {
  source = "../../../infrastructure/modules/worker"

  name = local.service_name

  deployment_service_env  = var.deployment_service_env
  deployment_service_name = var.deployment_service_name

  image = var.container_image

  env_vars = {
    sqs_queue_url     = module.indexer_input_queue.url
    metrics_namespace = local.service_name

    es_username = "sierra_indexer"
    es_protocol = "https"
    es_port     = "9243"
  }

  secret_env_vars = {
    es_password = "reporting/sierra_indexer/es_password"
    es_host     = "reporting/es_host"
  }

  cpu    = 1024
  memory = 2048

  min_capacity = 0

  # We don't allow this app to scale up too far -- it might take a while
  # to get through a reindex, but we don't want to overwhelm the
  # reporting cluster.
  max_capacity = 3

  namespace_id = var.namespace_id

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  subnets = var.subnets

  security_group_ids = [
    # TODO: Do we need this interservice security group?
    var.interservice_security_group_id,
    var.service_egress_security_group_id,
  ]
  elastic_cloud_vpce_sg_id = var.elastic_cloud_vpce_sg_id

  shared_logging_secrets = var.shared_logging_secrets

  use_fargate_spot = true
}
