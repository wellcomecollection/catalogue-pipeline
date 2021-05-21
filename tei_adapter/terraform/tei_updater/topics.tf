module "tei_adapter_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "tei-files"
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
