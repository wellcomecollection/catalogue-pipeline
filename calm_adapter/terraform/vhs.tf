resource "aws_iam_role_policy" "vhs_readwrite" {
  role   = module.task_definition.task_role_name
  policy = local.vhs_read_policy
}
