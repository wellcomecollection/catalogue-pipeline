resource "aws_iam_role_policy" "read_from_q" {
  role   = module.worker.task_role_name
  policy = module.queue.read_policy
}

resource "aws_iam_role_policy" "publish_to_topic" {
  role   = module.worker.task_role_name
  policy = module.mets_adapter_output_topic.publish_policy
}

resource "aws_iam_role_policy" "mets_adapter_dynamo_readwrite" {
  role   = module.worker.task_role_name
  policy = local.mets_full_access_policy
}
