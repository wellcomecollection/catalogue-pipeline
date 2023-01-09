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

  fargate_service_boilerplate = var.fargate_service_boilerplate

  security_group_ids = [
    # TODO: Do we need this interservice security group?
    var.interservice_security_group_id,
  ]
}
