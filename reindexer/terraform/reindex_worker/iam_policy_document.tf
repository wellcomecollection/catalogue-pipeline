data "aws_iam_policy_document" "vhs_read_policy" {
  statement {
    actions = [
      "dynamodb:Scan",
      "dynamodb:BatchGetItem",
    ]

    resources = [
      for job in var.reindexer_jobs :
      "arn:aws:dynamodb:${var.aws_region}:${var.account_id}:table/${job["table"]}"
    ]
  }
}

data "aws_iam_policy_document" "sns_publish_policy" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [for job in var.reindexer_jobs : job["topic"]]
  }
}
