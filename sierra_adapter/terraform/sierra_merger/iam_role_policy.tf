resource "aws_iam_role_policy" "read_write_vhs" {
  role   = module.sierra_merger.task_role_name
  policy = var.vhs_read_write_policy
}

resource "aws_iam_role_policy" "publish_to_sns" {
  role   = module.sierra_merger.task_role_name
  policy = module.output_topic.publish_policy
}
