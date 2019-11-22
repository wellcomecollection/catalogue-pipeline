module "queue" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs?ref=v11.6.0"
  queue_name  = "mets_adapter_queue"
  topic_names = ["${module.temp_test_topic.name}"] // change this when we get a topic from the storage service
  topic_count = 1

  aws_region    = "${local.aws_region}"
  account_id    = "${local.account_id}"
  alarm_topic_arn = "${local.dlq_alarm_arn}"
}