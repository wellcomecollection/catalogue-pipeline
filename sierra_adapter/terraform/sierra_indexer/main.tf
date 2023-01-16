locals {
  service_name = "${var.namespace}-sierra_indexer"
}

module "sierra_indexer" {
  source = "../../../pipeline/terraform/modules/fargate_service"

  name            = local.service_name
  container_image = var.container_image

  topic_arns = var.topic_arns

  env_vars = {
    metrics_namespace = local.service_name

    es_username = "sierra_indexer"
    es_protocol = "https"
    es_port     = "9243"

    # TODO: Change the Sierra indexer to look for the `queue_url` env var
    sqs_queue_url = module.sierra_indexer.queue_url
  }

  omit_queue_url = true

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

  # TODO: Does the Sierra adapter need service discovery?
  service_discovery_namespace_id = var.namespace_id

  fargate_service_boilerplate = var.fargate_service_boilerplate

  security_group_ids = [
    # TODO: Do we need this interservice security group?
    var.interservice_security_group_id,
  ]
}
