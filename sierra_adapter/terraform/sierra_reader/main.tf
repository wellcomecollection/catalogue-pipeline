locals {
  service_name = "${var.namespace}-${var.resource_type}-reader"
}

module "sierra_reader" {
  source = "../../../pipeline/terraform/modules/fargate_service"

  name            = local.service_name
  container_image = var.container_image

  topic_arns = var.windows_topic_arns

  queue_name = "${var.namespace}-sierra_${var.resource_type}_windows"

  # Ensure that messages are spread around -- if we get a timeout from the
  # Sierra API, we don't retry _too_ quickly.
  #
  # We also want this to be long enough that a reader can finish processing
  # the window it receives.  If the timeout is only 5 minutes, we've seen
  # messages get double-sent under heavy load.  Let's avoid that.
  queue_visibility_timeout_seconds = 1200

  # In certain periods of high activity, we've seen the Sierra API timeout
  # multiple times.  Since the reader can restart a partially-completed
  # window, it's okay to retry the window several times.
  max_receive_count = 12

  env_vars = {
    resource_type     = var.resource_type
    bucket_name       = var.bucket_name
    metrics_namespace = local.service_name
    sierra_api_url    = var.sierra_api_url
    sierra_fields     = var.sierra_fields
    batch_size        = 50

    # TODO: Change the Sierra reader to look for the `queue_url` env var
    windows_queue_url = module.sierra_reader.queue_url
  }

  omit_queue_url = true

  secret_env_vars = {
    sierra_oauth_secret = "sierra_adapter/sierra_api_client_secret"
    sierra_oauth_key    = "sierra_adapter/sierra_api_key"
  }

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
  from = module.sierra_reader_service
  to = module.sierra_reader.module.worker
}

moved {
  from = module.windows_queue
  to = module.sierra_reader.module.input_queue
}

moved {
  from = module.scaling_alarm
  to = module.sierra_reader.module.scaling_alarm
}

moved {
  from = aws_iam_role_policy.allow_read_from_windows_q
  to = module.sierra_reader.aws_iam_role_policy.read_from_q
}
