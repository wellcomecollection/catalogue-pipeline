resource "aws_iam_role" "read_storage_s3" {
  name               = "read_storage_s3_role"
  assume_role_policy = data.aws_iam_policy_document.no-assume-role-policy.json
}

data "aws_iam_policy_document" "no-assume-role-policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }
}

resource "aws_iam_role_policy" "storage_s3_read" {
  role   = aws_iam_role.read_storage_s3.name
  policy = data.aws_iam_policy_document.allow_storage_access.json
}

data "aws_iam_policy_document" "allow_storage_access" {
  statement {
    actions = [
      "s3:GetObject*",
      "s3:ListBucket",
    ]

    resources = ["arn:aws:s3:::${local.storage_bucket}", "arn:aws:s3:::${local.storage_bucket}/*"]
  }
}
