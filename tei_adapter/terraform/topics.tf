module "tei_id_extractor_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "tei-id-extractor"
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

resource "aws_iam_role_policy" "tei_github_policy" {
  role   = module.tei_id_extractor.task_role_name
  policy = data.aws_iam_policy_document.publish_to_tei_id_extractor_topic.json
}
