module "service" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/prebuilt/scaling?ref=v19.12.0"

  service_name = "mets_adapter"

  container_image = "${local.mets_adapter_image}"

  cluster_id   = "${aws_ecs_cluster.cluster.id}"
  cluster_name = "${aws_ecs_cluster.cluster.name}"

  subnets    = ["${local.private_subnets}"]
  aws_region = "${local.aws_region}"

  namespace_id = "${aws_service_discovery_private_dns_namespace.namespace.id}"

  cpu    = "256"
  memory = "512"

  security_group_ids = []

  min_capacity = 0
  max_capacity = 10

  env_vars = {
    logstash_host = "${local.logstash_host}"

    sns_arn              = "${module.mets_adapter_topic.arn}"
    queue_id = "${module.queue.id}"
    metrics_namespace    = "${local.namespace}"
    mets_adapter_dynamo_table = "${local.mets_adapter_table_name}"

    bag_api_url = "${local.bag_api_url}"
    oauth_url = "${local.oauth_url}"
  }

  env_vars_length = 7

  secret_env_vars = {
    oauth_client_id     = "mets_adapter/mets_adapter/client_id"
    oauth_secret     = "mets_adapter/mets_adapter/secret"
  }

  secret_env_vars_length = 2
}
