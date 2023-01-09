locals {
  service_name = "${var.namespace}-${var.resource_type}-linker"
}

module "sierra_linker" {
  source = "../../../pipeline/terraform/modules/fargate_service"

  name            = local.service_name
  container_image = var.container_image

  topic_arns        = [var.demultiplexer_topic_arn]
  max_receive_count = 10

  env_vars = {
    metrics_namespace = local.service_name

    dynamo_table_name = aws_dynamodb_table.links.name

    topic_arn = module.output_topic.arn

    resource_type = var.resource_type

    # TODO: Change the Sierra linker to look for the `queue_url` env var
    demultiplexer_queue_url = module.sierra_linker.queue_url
  }

  omit_queue_url = true

  min_capacity = 0
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
  to = module.sierra_linker.module.worker
}

moved {
  from = module.input_queue
  to   = module.sierra_linker.module.input_queue
}

moved {
  from = module.scaling_alarm
  to   = module.sierra_linker.module.scaling_alarm
}

moved {
  from = aws_iam_role_policy.read_from_q
  to    = module.sierra_linker.aws_iam_role_policy.read_from_q
}
