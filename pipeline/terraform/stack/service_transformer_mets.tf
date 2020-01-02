# Input queue
module "mets_transformer_queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=v11.6.0"
  queue_name  = "${local.namespace_hyphen}_mets_transformer"
  topic_names = ["${var.mets_adapter_topic_names}"]
  topic_count = "${var.mets_adapter_topic_count}"

  aws_region    = "${var.aws_region}"
  account_id    = "${var.account_id}"
  alarm_topic_arn = "${var.dlq_alarm_arn}"
}

# Service

module "mets_transformer" {
  source = "../modules/service"

  service_name = "${local.namespace_hyphen}_mets_transformer"

  container_image = "${local.transformer_mets_image}"

  security_group_ids = [
    "${module.egress_security_group.sg_id}",
    "${aws_security_group.interservice.id}",
  ]

  cluster_name = "${aws_ecs_cluster.cluster.name}"
  cluster_id   = "${aws_ecs_cluster.cluster.id}"

  namespace_id  = "${aws_service_discovery_private_dns_namespace.namespace.id}"
  logstash_host = "${local.logstash_host}"

  env_vars = {
    sns_arn              = "${module.mets_transformer_topic.arn}"
    transformer_queue_id = "${module.mets_transformer_queue.id}"
    metrics_namespace    = "${local.namespace_hyphen}_mets_transformer"
    messages_bucket_name = "${aws_s3_bucket.messages.id}"

    mets_adapter_dynamo_table_name = "${var.mets_adapter_table_name}"
    assume_role_arn = "${var.read_storage_s3_role_arn}"
  }

  env_vars_length = 6

  secret_env_vars        = {}
  secret_env_vars_length = "0"

  subnets    = ["${var.subnets}"]
  aws_region = "${var.aws_region}"
  max_capacity = 10
  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
  queue_read_policy = "${module.mets_transformer_queue.read_policy}"
}

# Output topic

module "mets_transformer_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_mets_transformer"
  role_names = ["${module.mets_transformer.task_role_name}"]

  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
}

module "mets_transformer_scaling_alarm" {
  source     = "git::https://github.com/wellcometrust/terraform-modules.git//autoscaling/alarms/queue?ref=v19.12.0"
  queue_name = "${module.mets_transformer_queue.name}"

  queue_high_actions = ["${module.mets_transformer.scale_up_arn}"]
  queue_low_actions  = ["${module.mets_transformer.scale_down_arn}"]
}

# Permissions

resource "aws_iam_role_policy" "read_mets_adapter_table" {
  role   = "${module.mets_transformer.task_role_name}"
  policy = "${var.mets_adapter_read_policy}"
}

data "aws_iam_policy_document" "assume_storage_read_role" {
  statement {
    effect = "Allow"
    actions = [
      "sts:AssumeRole"
    ]

    resources = [
      "${var.read_storage_s3_role_arn}"
    ]
  }
}

resource "aws_iam_role_policy" "assume_role_policy" {
  role   = "${module.mets_transformer.task_role_name}"
  policy = "${data.aws_iam_policy_document.assume_storage_read_role.json}"
}