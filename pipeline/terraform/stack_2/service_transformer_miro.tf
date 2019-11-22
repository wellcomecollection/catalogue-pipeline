# Input queue
module "miro_transformer_queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=v11.6.0"
  queue_name  = "${local.namespace_hyphen}_miro_transformer"
  topic_names = ["${var.miro_adapter_topic_names}"]
  topic_count = "${var.miro_adapter_topic_count}"

  aws_region    = "${var.aws_region}"
  account_id    = "${var.account_id}"
  alarm_topic_arn = "${var.dlq_alarm_arn}"
}

# Service

module "miro_transformer" {
  source = "../modules/service"

  service_name = "${local.namespace_hyphen}_miro_transformer"

  container_image = "${local.transformer_miro_image}"

  security_group_ids = [
    "${module.egress_security_group.sg_id}",
    "${aws_security_group.interservice.id}",
  ]

  cluster_name = "${aws_ecs_cluster.cluster.name}"
  cluster_id   = "${aws_ecs_cluster.cluster.id}"

  namespace_id  = "${aws_service_discovery_private_dns_namespace.namespace.id}"
  logstash_host = "${local.logstash_host}"

  env_vars = {
    sns_arn              = "${module.miro_transformer_topic.arn}"
    transformer_queue_id = "${module.miro_transformer_queue.id}"
    metrics_namespace    = "${local.namespace_hyphen}_miro_transformer"
    messages_bucket_name = "${aws_s3_bucket.messages.id}"
  }

  env_vars_length = 4

  secret_env_vars        = {}
  secret_env_vars_length = "0"

  subnets    = ["${var.subnets}"]
  aws_region = "${var.aws_region}"
  max_capacity = 10
  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
  queue_read_policy = "${module.miro_transformer_queue.read_policy}"
}

# Output topic

module "miro_transformer_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_miro_transformer"
  role_names = ["${module.miro_transformer.task_role_name}"]

  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
}

module "miro_transformer_scaling_alarm" {
  source     = "git::https://github.com/wellcometrust/terraform-modules.git//autoscaling/alarms/queue?ref=v19.12.0"
  queue_name = "${module.miro_transformer_queue.name}"

  queue_high_actions = ["${module.miro_transformer.scale_up_arn}"]
  queue_low_actions  = ["${module.miro_transformer.scale_down_arn}"]
}
