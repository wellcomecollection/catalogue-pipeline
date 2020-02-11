# Input queue

module "matcher_queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=v11.6.0"

  topic_names = [
    "${module.recorder_topic.name}",
  ]

  topic_count = 1

  queue_name = "${local.namespace_hyphen}_matcher"

  // The records in the locktable expire after 3 minutes
  // The matcher is able to override locks that have expired
  // Wait slightly longer to make sure locks are expired
  visibility_timeout_seconds = 210

  aws_region      = "${var.aws_region}"
  account_id      = "${var.account_id}"
  alarm_topic_arn = "${var.dlq_alarm_arn}"
}

# Service

module "matcher" {
  source = "../modules/service"

  security_group_ids = [
    "${module.egress_security_group.sg_id}",
    "${aws_security_group.interservice.id}",
  ]

  cluster_name  = "${aws_ecs_cluster.cluster.name}"
  cluster_id    = "${aws_ecs_cluster.cluster.id}"
  namespace_id  = "${aws_service_discovery_private_dns_namespace.namespace.id}"
  subnets       = "${var.subnets}"
  service_name  = "${local.namespace_hyphen}_matcher"
  aws_region    = "${var.aws_region}"
  logstash_host = "${local.logstash_host}"

  env_vars = {
    queue_url         = "${module.matcher_queue.id}"
    metrics_namespace = "${local.namespace_hyphen}_matcher"
    vhs_bucket_name   = "${module.vhs_recorder.bucket_name}"
    topic_arn         = "${module.matcher_topic.arn}"

    dynamo_table            = "${aws_dynamodb_table.matcher_graph_table.id}"
    dynamo_index            = "work-sets-index"
    dynamo_lock_table       = "${aws_dynamodb_table.matcher_lock_table.id}"
    dynamo_lock_table_index = "context-ids-index"

    vhs_recorder_dynamo_table_name = "${module.vhs_recorder.table_name}"
    vhs_recorder_bucket_name       = "${module.vhs_recorder.bucket_name}"
  }

  env_vars_length = 10

  secret_env_vars = {}

  secret_env_vars_length = "0"

  container_image     = "${local.matcher_image}"
  max_capacity        = 10
  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
  queue_read_policy   = "${module.matcher_queue.read_policy}"
}

# Permissions

resource "aws_iam_role_policy" "matcher_vhs_recorder_read" {
  role   = "${module.matcher.task_role_name}"
  policy = "${module.vhs_recorder.read_policy}"
}

resource "aws_iam_role_policy" "matcher_graph_readwrite" {
  role   = "${module.matcher.task_role_name}"
  policy = "${data.aws_iam_policy_document.graph_table_readwrite.json}"
}

resource "aws_iam_role_policy" "matcher_lock_readwrite" {
  role   = "${module.matcher.task_role_name}"
  policy = "${data.aws_iam_policy_document.lock_table_readwrite.json}"
}

# Output topic

module "matcher_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_matcher"
  role_names = ["${module.matcher.task_role_name}"]

  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
}

module "matcher_scaling_alarm" {
  source     = "git::https://github.com/wellcometrust/terraform-modules.git//autoscaling/alarms/queue?ref=v19.12.0"
  queue_name = "${module.matcher_queue.name}"

  queue_high_actions = ["${module.matcher.scale_up_arn}"]
  queue_low_actions  = ["${module.matcher.scale_down_arn}"]
}
