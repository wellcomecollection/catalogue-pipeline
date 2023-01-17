resource "aws_iam_role_policy" "publish_to_topic" {
  role   = module.mets_adapter.task_role_name
  policy = module.mets_adapter_output_topic.publish_policy
}

resource "aws_iam_role_policy" "mets_adapter_dynamo_readwrite" {
  role   = module.mets_adapter.task_role_name
  policy = local.mets_full_access_policy
}
