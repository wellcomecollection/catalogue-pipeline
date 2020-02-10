resource "aws_iam_role_policy" "read_from_q" {
  role   = "${module.service.task_role_name}"
  policy = "${var.queue_read_policy}"
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

resource "aws_iam_role_policy" "task_s3_messages_get" {
  role   = "${module.service.task_role_name}"
  policy = "${data.aws_iam_policy_document.allow_s3_messages_get.json}"
}

data "aws_iam_policy_document" "allow_s3_messages_get" {
  statement {
    actions = [
      "s3:GetObject",
    ]

    resources = [
      "${var.messages_bucket_arn}/*",
    ]
  }
}
