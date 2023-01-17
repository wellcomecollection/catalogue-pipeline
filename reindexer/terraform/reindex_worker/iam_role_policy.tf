resource "aws_iam_role_policy" "allow_vhs_read" {
  role   = module.reindex_worker.task_role_name
  policy = data.aws_iam_policy_document.vhs_read_policy.json
}

resource "aws_iam_role_policy" "allow_publish_to_sns" {
  role   = module.reindex_worker.task_role_name
  policy = data.aws_iam_policy_document.sns_publish_policy.json
}
