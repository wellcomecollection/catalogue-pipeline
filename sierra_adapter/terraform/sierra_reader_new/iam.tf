resource "aws_iam_role_policy" "allow_sns_publish" {
  role   = module.lambda.role_name
  policy = module.output_topic.publish_policy
}

resource "aws_iam_role_policy" "allow_secrets_read" {
  role   = module.lambda.role_name
  policy = data.aws_iam_policy_document.allow_secrets_read.json
}

data "aws_iam_policy_document" "allow_secrets_read" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
    ]

    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:sierra_adapter/*",
    ]
  }
}

resource "aws_iam_role_policy" "allow_s3_access" {
  role   = module.lambda.role_name
  policy = data.aws_iam_policy_document.allow_s3_access.json
}

data "aws_s3_bucket" "sierra_data" {
  bucket = var.reader_bucket
}

data "aws_iam_policy_document" "allow_s3_access" {
  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "${data.aws_s3_bucket.sierra_data.arn}/windows_${var.resource_type}_complete/*",
    ]
  }
}
