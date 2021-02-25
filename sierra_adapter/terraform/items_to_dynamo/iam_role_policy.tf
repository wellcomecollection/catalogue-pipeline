resource "aws_iam_role_policy" "allow_dynamo_access" {
  role   = module.service.task_role_name
  policy = data.aws_iam_policy_document.allow_dynamodb_access.json
}

resource "aws_iam_role_policy" "allow_read_from_demux_q" {
  role   = module.service.task_role_name
  policy = module.demultiplexer_queue.read_policy
}

resource "aws_iam_role_policy" "push_cloudwatch_metric" {
  role   = module.service.task_role_name
  policy = data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json
}

resource "aws_iam_role_policy" "allow_sns_topic_publish" {
  role   = module.service.task_role_name
  policy = module.sierra_to_dynamo_updates_topic.publish_policy
}
