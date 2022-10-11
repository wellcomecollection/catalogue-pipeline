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

data "aws_iam_policy_document" "allow_external_subscribers" {
  statement {
    actions = [
      "sns:Subscribe",
    ]
    principals {
      identifiers = var.subscriber_accounts
      type        = "AWS"
    }
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
