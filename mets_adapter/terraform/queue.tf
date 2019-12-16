module "queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=use_topic_arns_queue"
  queue_name  = "mets_adapter_queue"
  topic_arns = ["${local.storage_notifications_topic_arn}", "${module.temp_test_topic.arn}"]
  topic_count = 2

  aws_region    = "${local.aws_region}"
  account_id    = "${local.account_id}"
  alarm_topic_arn = "${local.dlq_alarm_arn}"
}