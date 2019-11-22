resource "aws_iam_role_policy" "read_from_q" {
  role = "${module.service.task_role_name}"
  policy = "${module.queue.read_policy}"
}

resource "aws_iam_role_policy" "mets_adapter_vhs_readwrite" {
  role   = "${module.service.task_role_name}"
  policy = "${local.vhs_mets_full_access_policy}"
}

resource "aws_iam_role_policy" "cloudwatch_push_metrics" {
  role   = "${module.service.task_role_name}"
  policy = "${data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json}"
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
