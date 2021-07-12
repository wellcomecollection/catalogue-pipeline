module "calm_adapter_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "calm-records"
}

module "calm_deletions_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "calm-deletions"
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

data "aws_iam_policy_document" "publish_to_deletions_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      module.calm_deletions_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "adapter_policy" {
  role   = module.adapter_worker.task_role_name
  policy = data.aws_iam_policy_document.publish_to_adapter_topic.json
}

resource "aws_iam_role_policy" "deletion_checker_policy" {
  role   = module.deletion_checker_worker.task_role_name
  policy = data.aws_iam_policy_document.publish_to_deletions_topic.json
}

resource "aws_sns_topic" "calm_windows_topic" {
  name = "calm-windows"
}
