module "tei_id_extractor_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "tei-id-extractor-output"
}

module "tei_adapter_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "tei-adapter-output"
}

data "aws_iam_policy_document" "publish_to_tei_id_extractor_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      module.tei_id_extractor_topic.arn
    ]
  }
}

data "aws_iam_policy_document" "publish_to_tei_adapter_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      module.tei_adapter_topic.arn
    ]
  }
}
