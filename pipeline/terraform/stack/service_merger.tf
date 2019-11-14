# Input queue

module "merger_queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=v11.6.0"
  queue_name  = "${local.namespace_underscores}_merger"
  topic_names = ["${module.matcher_topic.name}"]
  topic_count = 1

  aws_region    = "${var.aws_region}"
  account_id    = "${var.account_id}"
  alarm_topic_arn = "${var.dlq_alarm_arn}"
}

# Service

module "merger" {
  source = "../modules/service"

  security_group_ids = [
    "${module.egress_security_group.sg_id}",
    "${aws_security_group.interservice.id}",
  ]

  cluster_name  = "${aws_ecs_cluster.cluster.name}"
  cluster_id    = "${aws_ecs_cluster.cluster.id}"
  namespace_id  = "${aws_service_discovery_private_dns_namespace.namespace.id}"
  subnets       = "${var.subnets}"
  service_name  = "${local.namespace_underscores}_merger"
  aws_region    = "${var.aws_region}"
  logstash_host = "${local.logstash_host}"

  env_vars = {
    metrics_namespace        = "${local.namespace_underscores}_merger"
    messages_bucket_name     = "${aws_s3_bucket.messages.id}"
    topic_arn                = "${module.matcher_topic.arn}"
    merger_queue_id          = "${module.merger_queue.id}"
    merger_topic_arn         = "${module.merger_topic.arn}"
    vhs_recorder_bucket_name = "${module.vhs_recorder.bucket_name}"
    vhs_recorder_table_name  = "${module.vhs_recorder.table_name}"
  }

  env_vars_length = 7

  secret_env_vars = {}

  secret_env_vars_length = "0"

  container_image = "${local.merger_image}"
  max_capacity = 10
  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"

  queue_read_policy = "${module.merger_queue.read_policy}"
}

# Permissions

resource "aws_iam_role_policy" "merger_vhs_recorder_read" {
  role   = "${module.merger.task_role_name}"
  policy = "${module.vhs_recorder.read_policy}"
}

# Output topic

module "merger_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_underscores}_merger"
  role_names = ["${module.merger.task_role_name}"]

  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
}

module "merger_scaling_alarm" {
  source     = "git::https://github.com/wellcometrust/terraform-modules.git//autoscaling/alarms/queue?ref=v19.12.0"
  queue_name = "${module.merger_queue.name}"

  queue_high_actions = ["${module.merger.scale_up_arn}"]
  queue_low_actions  = ["${module.merger.scale_down_arn}"]
}
