resource "aws_iam_role_policy" "read_from_q" {
  role = "${module.service.task_role_name}"
  policy = "${module.queue.read_policy}"
}

resource "aws_iam_role_policy" "publish_to_topic" {
  role = "${module.service.task_role_name}"
  policy = "${module.mets_vhs_keys_topic.publish_policy}"
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
resource "aws_iam_role_policy" "storage_s3_read" {
  role   = "${module.service.task_role_name}"
  policy = "${data.aws_iam_policy_document.allow_storage_access.json}"
}

data "aws_iam_policy_document" "allow_storage_access" {
  statement {
    actions = [
      "s3:GetObject*",
      "s3:ListBucket",
    ]

    resources = ["arn:aws:s3:::${local.storage_bucket}", "arn:aws:s3:::${local.storage_bucket}/*"]
  }
}