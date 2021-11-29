resource "aws_iam_role_policy" "read_from_q" {
  role   = module.worker.task_role_name
  policy = var.queue_read_policy
}

resource "aws_iam_role_policy" "cloudwatch_push_metrics" {
  role   = module.worker.task_role_name
  policy = data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json
}

data "aws_iam_policy_document" "allow_cloudwatch_push_metrics" {
  statement {
    actions = [
      "cloudwatch:PutMetricData",
    ]

    resources = [
      "*",
    ]
  }
}
