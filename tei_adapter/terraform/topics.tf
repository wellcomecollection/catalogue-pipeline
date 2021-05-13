module "tei_adapter_topic" {
  source                         = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name                           = "tei-files"
}


data "aws_iam_policy_document" "publish_to_adapter_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      module.tei_adapter_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "tei_github_policy" {
  role   = module.tei_github.task_role_name
  policy = data.aws_iam_policy_document.publish_to_adapter_topic.json
}
