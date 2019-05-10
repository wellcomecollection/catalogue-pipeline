resource "aws_s3_bucket" "bucket" {
  count  = "${var.prevent_destroy == "true" && var.bucket_name == "" ? 1 : 0}"
  bucket = "${local.bucket_name}"

  lifecycle {
    prevent_destroy = true
  }

  policy = "${length(var.s3_cross_account_access_accounts) == 0 ? "" : data.aws_iam_policy_document.allow_s3_cross_account_access_perm.json}"
}

resource "aws_s3_bucket" "transient_bucket" {
  count         = "${var.prevent_destroy == "false" && var.bucket_name == "" ? 1 : 0}"
  force_destroy = true

  bucket = "${local.bucket_name}"
  policy = "${length(var.s3_cross_account_access_accounts) == 0 ? "" : data.aws_iam_policy_document.allow_s3_cross_account_access_perm.json}"
}

data "aws_iam_policy_document" "allow_s3_cross_account_access_perm" {
  statement {
    sid    = "AllowS3CrossAccountAccess"
    effect = "Allow"

    principals {
      identifiers = "${formatlist("arn:aws:iam::%s:root", var.s3_cross_account_access_accounts)}"
      type        = "AWS"
    }

    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::${local.bucket_name}/*"]
  }
}
