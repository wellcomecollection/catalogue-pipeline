# logstash_transit

module "logstash_transit" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/prebuilt/default?ref=4ceb43fb9c08f5ac8ec3bc43b03f8c3c81621b97"

  security_group_ids = [
    "${aws_security_group.interservice.id}",
    "${aws_security_group.service_egress.id}",
  ]

  cluster_id   = "${aws_ecs_cluster.cluster.id}"
  namespace_id = "${aws_service_discovery_private_dns_namespace.namespace.id}"
  subnets      = "${var.private_subnets}"
  service_name = "${local.logstash_transit_service_name}"

  env_vars = {
    XPACK_MONITORING_ENABLED = "false"
  }

  env_vars_length = 1

  secret_env_vars = {
    ES_HOST     = "catalogue/logstash/es_host"
    ES_USER     = "catalogue/logstash/es_user"
    ES_PASS     = "catalogue/logstash/es_pass"
  }

  secret_env_vars_length = 3

  cpu    = 1024
  memory = 2048

  container_image = "${local.logstash_transit_image}"
}