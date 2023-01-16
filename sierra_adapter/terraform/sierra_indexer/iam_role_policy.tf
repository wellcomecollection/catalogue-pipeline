resource "aws_iam_role_policy" "get_from_s3" {
  role   = module.sierra_indexer.task_role_name
  policy = data.aws_iam_policy_document.get_from_s3.json
}
