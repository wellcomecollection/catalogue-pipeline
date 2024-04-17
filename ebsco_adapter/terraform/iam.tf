resource "aws_iam_role_policy" "publish_to_topic" {
  policy = module.ebsco_adapter_output_topic.publish_policy
  role   = module.ftp_lambda.lambda_role.name
}

data "aws_iam_policy_document" "ebsco_s3_bucket_full_access" {
  statement {
    actions = [
      "s3:ListBucket",
    ]

    resources = [
      aws_s3_bucket.ebsco_adapter.arn
    ]
  }

  statement {
    actions = [
      "s3:*Object",
    ]

    resources = [
      "${aws_s3_bucket.ebsco_adapter.arn}/*"
    ]
  }
}

resource "aws_iam_policy" "ebsco_s3_bucket_full_access" {
  name        = "ebsco_s3_bucket_full_access"
  description = "Allow full access to the ebsco_adapter S3 bucket"
  policy      = data.aws_iam_policy_document.ebsco_s3_bucket_full_access.json
}

resource "aws_iam_role_policy_attachment" "ebsco_s3_bucket_full_access" {
  policy_arn = aws_iam_policy.ebsco_s3_bucket_full_access.arn
  role       = module.ftp_lambda.lambda_role.name
}
