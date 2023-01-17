data "aws_s3_bucket" "sierra_adapter" {
  bucket = var.sierra_adapter_bucket
}

data "aws_iam_policy_document" "get_from_s3" {
  statement {
    actions = [
      "s3:Get*",
    ]

    resources = [
      data.aws_s3_bucket.sierra_adapter.arn,
      "${data.aws_s3_bucket.sierra_adapter.arn}/*",
    ]
  }
}
