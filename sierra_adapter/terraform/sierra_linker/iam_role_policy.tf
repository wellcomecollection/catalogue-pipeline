resource "aws_iam_role_policy" "read_write_dynamo" {
  role   = module.service.task_role_name
  policy = data.aws_iam_policy_document.read_write_dynamo.json
}

resource "aws_iam_role_policy" "read_from_q" {
  role   = module.service.task_role_name
  policy = module.input_queue.read_policy
}

resource "aws_iam_role_policy" "publish_to_sns" {
  role   = module.service.task_role_name
  policy = module.output_topic.publish_policy
}
