resource "aws_s3_bucket" "bucket" {
  bucket = local.bucket_name
  policy = data.aws_iam_policy_document.bucket_policy.json

  lifecycle {
    prevent_destroy = true
  }
}

data "aws_iam_policy_document" "bucket_policy" {
  statement {
    principals {
      identifiers = var.read_principles
      type        = "AWS"
    }

    actions = [
      "s3:Get*",
      "s3:List*",
    ]

    resources = [
      "arn:aws:s3:::${local.bucket_name}/*",
      "arn:aws:s3:::${local.bucket_name}",
    ]
  }
}
