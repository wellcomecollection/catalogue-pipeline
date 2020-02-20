resource "aws_sns_topic" "calm_adapter_topic" {
  name = "calm-records"
}

data "aws_iam_policy_document" "publish_to_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      aws_sns_topic.calm_adapter_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "task_sns" {
  role   = module.task_definition.task_role_name
  policy = data.aws_iam_policy_document.publish_to_topic.json
}
