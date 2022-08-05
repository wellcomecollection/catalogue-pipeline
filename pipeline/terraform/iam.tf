data "aws_iam_policy_document" "read_tei_adapter_bucket" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject*",
    ]

    resources = [
      "arn:aws:s3:::${local.tei_adapter_bucket}",
      "arn:aws:s3:::${local.tei_adapter_bucket}/*",
    ]
  }
}

data "aws_iam_policy_document" "read_storage_bucket" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject*",
    ]

    resources = [
      "arn:aws:s3:::${local.storage_bucket}",
      "arn:aws:s3:::${local.storage_bucket}/*",
    ]
  }
}
