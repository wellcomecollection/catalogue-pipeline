resource "aws_iam_role_policy" "get_from_s3" {
  role   = module.service.task_role_name
  policy = data.aws_iam_policy_document.get_from_s3.json
}

resource "aws_iam_role_policy" "read_from_q" {
  role   = module.service.task_role_name
  policy = module.indexer_input_queue.read_policy
}
