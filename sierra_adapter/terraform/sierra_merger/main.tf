locals {
  service_name = "${var.namespace}-${var.resource_type}-merger"
}

module "sierra_merger" {
  source = "../../../pipeline/terraform/modules/fargate_service"

  name            = local.service_name
  container_image = var.container_image

  topic_arns = [var.updates_topic_arn]

  # Ensure that messages are spread around -- if the merger has an error
  # (for example, hitting DynamoDB write limits), we don't retry too quickly.
  queue_visibility_timeout_seconds = 300

  queue_name = "${var.namespace}-sierra_${var.resource_type}_merger_input"

  env_vars = {
    sierra_vhs_dynamo_table_name = var.vhs_table_name
    sierra_vhs_bucket_name       = var.vhs_bucket_name

    topic_arn         = module.output_topic.arn

    metrics_namespace = local.service_name

    resource_type = var.resource_type

    # TODO: Change the Sierra merger to look for the `queue_url` env var
    windows_queue_url = module.sierra_merger.queue_url
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

moved {
  from = module.service
  to = module.sierra_merger.module.worker
}

moved {
  from = module.input_queue
  to   = module.sierra_merger.module.input_queue
}

moved {
  from = module.scaling_alarm
  to   = module.sierra_merger.module.scaling_alarm
}

moved {
  from = aws_iam_role_policy.read_from_q
  to    = module.sierra_merger.aws_iam_role_policy.read_from_q
}
