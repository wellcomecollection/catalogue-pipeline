locals {
  _task_definition_split     = split(":", module.task_definition.arn)
  task_definition_version    = element(local._task_definition_split, length(local._task_definition_split) - 1)
  task_definition_arn_latest = trimsuffix(module.task_definition.arn, ":${local.task_definition_version}")
}

data "aws_iam_policy_document" "invoke_policy_document" {
  statement {
    effect = "Allow"
    actions = [
      "ecs:RunTask",
    ]
    resources = [
      "${local.task_definition_arn_latest}:*",
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "iam:PassRole",
    ]
    resources = [
      module.task_definition.task_execution_role_arn,
      module.task_definition.task_role_arn,
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]

    resources = [
      "*",
    ]
  }
}