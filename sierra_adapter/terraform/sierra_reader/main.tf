data "aws_ecs_cluster" "cluster" {
  cluster_name = "${var.cluster_name}"
}

module "sierra_reader_service" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/prebuilt/scaling?ref=v19.12.0"

  service_name = "${local.service_name}"

  container_image = "${var.container_image}"

  cluster_id   = "${data.aws_ecs_cluster.cluster.id}"
  cluster_name = "${var.cluster_name}"

  subnets    = "${var.subnets}"
  aws_region = "${var.aws_region}"

  namespace_id = "${var.namespace_id}"

  cpu    = 256
  memory = 512

  min_capacity = 0
  max_capacity = 3

  security_group_ids = [
    "${var.interservice_security_group_id}",
    "${var.service_egress_security_group_id}",
  ]

  env_vars = {
    resource_type     = "${var.resource_type}"
    windows_queue_url = "${module.windows_queue.id}"
    bucket_name       = "${var.bucket_name}"
    metrics_namespace = "${local.service_name}"
    sierra_api_url    = "${var.sierra_api_url}"
    sierra_fields     = "${var.sierra_fields}"
    batch_size        = 50
  }
  env_vars_length = 7

  secret_env_vars = {
    sierra_oauth_secret = "sierra_adapter/sierra_api_client_secret"
    sierra_oauth_key    = "sierra_adapter/sierra_api_key"
  }
  secret_env_vars_length = 2
}

