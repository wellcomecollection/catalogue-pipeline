data "aws_ecs_cluster" "cluster" {
  cluster_name = "${var.cluster_name}"
}

module "sierra_merger_service" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/prebuilt/scaling?ref=v19.12.0"

  service_name       = "sierra_${local.resource_type_singular}_merger"

  container_image = "${var.container_image}"

  security_group_ids = [
    "${var.interservice_security_group_id}",
    "${var.service_egress_security_group_id}",
  ]

  cluster_id   = "${data.aws_ecs_cluster.cluster.id}"
  cluster_name = "${var.cluster_name}"

  cpu    = 256
  memory = 512

  min_capacity = 0
  max_capacity = 3

  env_vars = {
    windows_queue_url = "${module.updates_queue.id}"
    metrics_namespace = "sierra_${local.resource_type_singular}_merger"
    dynamo_table_name = "${var.merged_dynamo_table_name}"
    bucket_name       = "${var.bucket_name}"
    topic_arn         = "${module.sierra_bib_merger_results.arn}"
  }

  env_vars_length = 5

  aws_region = "${var.aws_region}"
  subnets    = ["${var.subnets}"]

  namespace_id = "${var.namespace_id}"

  secret_env_vars = {
  }
  secret_env_vars_length = 0
}

