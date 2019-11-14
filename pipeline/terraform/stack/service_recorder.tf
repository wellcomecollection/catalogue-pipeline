# Input queue

module "recorder_queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=v11.6.0"
  queue_name  = "${local.namespace_underscores}_recorder"
  topic_names = [
    "${module.miro_transformer_topic.name}",
    "${module.sierra_transformer_topic.name}",
  ]

  topic_count = 2

  aws_region    = "${var.aws_region}"
  account_id    = "${var.account_id}"
  alarm_topic_arn = "${var.dlq_alarm_arn}"
}

# Service

module "recorder" {
  source = "../modules/service"

  security_group_ids = [
    "${module.egress_security_group.sg_id}",
    "${aws_security_group.interservice.id}",
  ]

  cluster_name  = "${aws_ecs_cluster.cluster.name}"
  cluster_id    = "${aws_ecs_cluster.cluster.id}"
  namespace_id  = "${aws_service_discovery_private_dns_namespace.namespace.id}"
  subnets       = "${var.subnets}"
  service_name  = "${local.namespace_underscores}_recorder"
  aws_region    = "${var.aws_region}"
  logstash_host = "${local.logstash_host}"

  env_vars = {
    recorder_queue_url = "${module.recorder_queue.id}"
    metrics_namespace  = "${local.namespace_underscores}_recorder"

    vhs_recorder_dynamo_table_name = "${module.vhs_recorder.table_name}"
    vhs_recorder_bucket_name       = "${module.vhs_recorder.bucket_name}"

    sns_topic = "${module.recorder_topic.arn}"
  }

  env_vars_length = 5

  secret_env_vars = {}

  secret_env_vars_length = "0"

  container_image = "${local.recorder_image}"
  max_capacity = 10
  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
  queue_read_policy = "${module.recorder_queue.read_policy}"
}

# Permissions

resource "aws_iam_role_policy" "recorder_vhs_recorder_readwrite" {
  role   = "${module.recorder.task_role_name}"
  policy = "${module.vhs_recorder.full_access_policy}"
}

# Output topic

module "recorder_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_underscores}_recorder"
  role_names = ["${module.recorder.task_role_name}"]

  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
}

module "recorder_scaling_alarm" {
  source     = "git::https://github.com/wellcometrust/terraform-modules.git//autoscaling/alarms/queue?ref=v19.12.0"
  queue_name = "${module.recorder_queue.name}"

  queue_high_actions = ["${module.recorder.scale_up_arn}"]
  queue_low_actions  = ["${module.recorder.scale_down_arn}"]
}
