# Input queue

module "ingestor_queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=v11.6.0"
  queue_name  = "${local.namespace_underscores}_ingestor"
  topic_names = ["${module.id_minter_topic.name}"]
  topic_count = 1

  aws_region    = "${var.aws_region}"
  account_id    = "${var.account_id}"
  alarm_topic_arn = "${var.dlq_alarm_arn}"
}

# Service

module "ingestor" {
  source = "../modules/service"

  service_name = "${local.namespace_underscores}_ingestor"

  container_image = "${local.ingestor_image}"

  security_group_ids = [
    "${module.egress_security_group.sg_id}",
    "${aws_security_group.interservice.id}",
  ]

  cluster_name  = "${aws_ecs_cluster.cluster.name}"
  cluster_id    = "${aws_ecs_cluster.cluster.id}"
  namespace_id  = "${aws_service_discovery_private_dns_namespace.namespace.id}"
  subnets       = "${var.subnets}"
  aws_region    = "${var.aws_region}"
  logstash_host = "${local.logstash_host}"

  env_vars = {
    metrics_namespace = "${local.namespace_underscores}_ingestor"
    es_index          = "${var.es_works_index}"
    ingest_queue_id   = "${module.ingestor_queue.id}"
  }

  env_vars_length = 3

  secret_env_vars = {
    es_host     = "catalogue/ingestor/es_host"
    es_port     = "catalogue/ingestor/es_port"
    es_username = "catalogue/ingestor/es_username"
    es_password = "catalogue/ingestor/es_password"
    es_protocol = "catalogue/ingestor/es_protocol"
  }

  secret_env_vars_length = "5"
  max_capacity = 10
  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
  queue_read_policy = "${module.ingestor_queue.read_policy}"
}

module "ingestor_scaling_alarm" {
  source     = "git::https://github.com/wellcometrust/terraform-modules.git//autoscaling/alarms/queue?ref=v19.12.0"
  queue_name = "${module.ingestor_queue.name}"

  queue_high_actions = ["${module.ingestor.scale_up_arn}"]
  queue_low_actions  = ["${module.ingestor.scale_down_arn}"]
}
