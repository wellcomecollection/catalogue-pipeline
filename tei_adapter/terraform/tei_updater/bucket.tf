
resource "aws_s3_bucket" "tei_adapter" {
  bucket = "wellcomecollection-platform-${var.namespace}"

  lifecycle {
    prevent_destroy = false
  }
}


data "aws_iam_policy_document" "allow_s3_read_write" {
  statement {
    actions = [
      "s3:Get*",
      "s3:List*",
      "s3:Put*",
    ]

    resources = [
      aws_s3_bucket.tei_adapter.arn,
      "${aws_s3_bucket.tei_adapter.arn}/*",
    ]
  }
}
