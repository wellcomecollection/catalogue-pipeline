module "sierra_bib_merger_results" {
  source = "git::https://github.com/wellcometrust/terraform.git//sns?ref=v1.0.0"
  name   = "sierra_bib_merger_results"
}

resource "aws_sns_topic_policy" "allow_reporting_subscription" {
  arn    = "${module.sierra_bib_merger_results.arn}"
  policy = "${data.aws_iam_policy_document.sns-topic-policy.json}"
}

data "aws_iam_policy_document" "sns-topic-policy" {
  policy_id = "__default_policy_ID"

  statement {
    actions = [
      "SNS:Subscribe",
      "SNS:SetTopicAttributes",
      "SNS:RemovePermission",
      "SNS:Receive",
      "SNS:Publish",
      "SNS:ListSubscriptionsByTopic",
      "SNS:GetTopicAttributes",
      "SNS:DeleteTopic",
      "SNS:AddPermission",
    ]

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceOwner"

      values = [
        "${var.account_id}",
      ]
    }

    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    resources = [
      "${module.sierra_bib_merger_results.arn}",
    ]

    sid = "__default_statement_ID"
  }

  statement {
    effect = "Allow"

    actions = [
      "SNS:Subscribe",
      "SNS:ListSubscriptionsByTopic",
      "SNS:Receive",
    ]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::269807742353:root"]
    }

    resources = [
      "${module.sierra_bib_merger_results.arn}",
    ]

    sid = "ReportingAccess"
  }
}
