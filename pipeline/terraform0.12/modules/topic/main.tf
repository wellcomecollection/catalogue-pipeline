resource "aws_sns_topic" "topic" {
  name = var.name
}

data "aws_iam_policy_document" "publish_to_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      aws_sns_topic.topic.arn,
    ]
  }
}

resource "aws_iam_role_policy" "task_sns" {
  count = length(var.role_names)

  role   = var.role_names[count.index]
  policy = data.aws_iam_policy_document.publish_to_topic.json
}

resource "aws_iam_role_policy" "task_s3_messages_put" {
  count = length(var.role_names)

  role   = var.role_names[count.index]
  policy = data.aws_iam_policy_document.allow_s3_messages_put.json
}

data "aws_iam_policy_document" "allow_s3_messages_put" {
  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "${var.messages_bucket_arn}/*",
    ]
  }
}
