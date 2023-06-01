data "aws_iam_policy_document" "allow_s3_access" {
  statement {
    actions = [
      "s3:*",
    ]

    resources = [
      "arn:aws:s3:::${var.s3_adapter_bucket_name}/completed*",
    ]
  }

  statement {
    actions = [
      "s3:List*",
    ]

    resources = [
      "arn:aws:s3:::${var.s3_adapter_bucket_name}",
      "arn:aws:s3:::${var.s3_adapter_bucket_name}/*",
    ]
  }
}

resource "aws_iam_role_policy" "allow_s3_access" {
  role   = module.lambda.role_name
  policy = data.aws_iam_policy_document.allow_s3_access.json
}

data "aws_iam_policy_document" "allow_secrets_read" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
    ]

    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:sierra_adapter/critical_slack_webhook*",
    ]
  }
}

resource "aws_iam_role_policy" "allow_secrets_read" {
  role   = module.lambda.role_name
  policy = data.aws_iam_policy_document.allow_secrets_read.json
}
