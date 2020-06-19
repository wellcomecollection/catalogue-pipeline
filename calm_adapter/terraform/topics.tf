module "calm_adapter_topic" {
  source                         = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name                           = "calm-records"
  cross_account_subscription_ids = ["269807742353"]
}

data "aws_iam_policy_document" "publish_to_adapter_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      module.calm_adapter_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "adapter_policy" {
  role   = module.worker.task_role_name
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
