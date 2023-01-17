resource "aws_iam_role_policy" "read_write_dynamo" {
  role   = module.sierra_linker.task_role_name
  policy = data.aws_iam_policy_document.read_write_dynamo.json
}

resource "aws_iam_role_policy" "publish_to_sns" {
  role   = module.sierra_linker.task_role_name
  policy = module.output_topic.publish_policy
}
