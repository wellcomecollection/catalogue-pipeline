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

  fargate_service_boilerplate = {
    cluster_name = var.cluster_name
    cluster_arn  = var.cluster_arn

    subnets = var.subnets

    elastic_cloud_vpce_security_group_id = var.elastic_cloud_vpce_sg_id

    dlq_alarm_topic_arn = var.dlq_alarm_arn

    shared_logging_secrets = var.shared_logging_secrets

    egress_security_group_id = var.service_egress_security_group_id
  }

  security_group_ids = [
    # TODO: Do we need this interservice security group?
    var.interservice_security_group_id,
  ]
}

moved {
  from = module.service
  to = module.sierra_indexer.module.worker
}

moved {
  from = module.indexer_input_queue
  to   = module.sierra_indexer.module.input_queue
}

moved {
  from = module.scaling_alarm
  to   = module.sierra_indexer.module.scaling_alarm
}

moved {
  from = aws_iam_role_policy.read_from_q
  to    = module.sierra_indexer.aws_iam_role_policy.read_from_q
}
