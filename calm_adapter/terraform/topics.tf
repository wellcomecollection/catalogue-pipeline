resource "aws_sns_topic" "calm_adapter_topic" {
  name = "calm-records"
}

data "aws_iam_policy_document" "publish_to_adapter_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      aws_sns_topic.calm_adapter_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "adapter_policy" {
  role   = module.task_definition.task_role_name
  policy = data.aws_iam_policy_document.publish_to_adapter_topic.json
}

resource "aws_sns_topic" "calm_windows_topic" {
  name = "calm-windows"
}

data "aws_iam_policy_document" "publish_to_windows_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      aws_sns_topic.calm_windows_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "windows_policy" {
  role   = aws_iam_role.window_generator_role.name
  policy = data.aws_iam_policy_document.publish_to_windows_topic.json
}
