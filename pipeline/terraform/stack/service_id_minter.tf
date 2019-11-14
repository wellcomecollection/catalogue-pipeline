# Input queue

module "id_minter_queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=v11.6.0"
  queue_name  = "${local.namespace_underscores}_id_minter"
  topic_names = ["${module.merger_topic.name}"]
  topic_count = 1

  aws_region    = "${var.aws_region}"
  account_id    = "${var.account_id}"
  alarm_topic_arn = "${var.dlq_alarm_arn}"
}


# Service

module "id_minter" {
  source = "../modules/service"

  service_name = "${local.namespace_underscores}_id_minter"

  container_image = "${local.id_minter_image}"

  security_group_ids = [
    "${module.egress_security_group.sg_id}",
    "${var.rds_ids_access_security_group_id}",
    "${aws_security_group.interservice.id}",
  ]

  cluster_name  = "${aws_ecs_cluster.cluster.name}"
  cluster_id    = "${aws_ecs_cluster.cluster.id}"
  namespace_id  = "${aws_service_discovery_private_dns_namespace.namespace.id}"
  subnets       = "${var.subnets}"
  aws_region    = "${var.aws_region}"
  logstash_host = "${local.logstash_host}"

  env_vars = {
    metrics_namespace    = "${local.namespace_underscores}_id_minter"
    messages_bucket_name = "${aws_s3_bucket.messages.id}"

    queue_url       = "${module.id_minter_queue.id}"
    topic_arn       = "${module.id_minter_topic.arn}"
    max_connections = 8
  }

  env_vars_length = 5

  secret_env_vars = {
    cluster_url = "catalogue/id_minter/rds_host"
    db_port     = "catalogue/id_minter/rds_port"
    db_username = "catalogue/id_minter/rds_user"
    db_password = "catalogue/id_minter/rds_password"
  }

  secret_env_vars_length = "4"

  // The maximum number of connections to RDS is 45.
  // Each id minter task is configured to have 8 connections
  // in the connection pool (see `max_connections` parameterabove).
  // To avoid exceeding the maximum nuber of connections to RDS,
  // the maximum capacity for the id minter should be no higher than 5
  max_capacity = 5
  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
  queue_read_policy = "${module.id_minter_queue.read_policy}"
}

# Output topic

module "id_minter_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_underscores}_id_minter"
  role_names = ["${module.id_minter.task_role_name}"]

  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
}

module "id_minter_scaling_alarm" {
  source     = "git::https://github.com/wellcometrust/terraform-modules.git//autoscaling/alarms/queue?ref=v19.12.0"
  queue_name = "${module.id_minter_queue.name}"

  queue_high_actions = ["${module.id_minter.scale_up_arn}"]
  queue_low_actions  = ["${module.id_minter.scale_down_arn}"]
}
