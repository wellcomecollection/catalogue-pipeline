resource "aws_iam_role_policy" "publish_to_sns" {
  role   = module.lambda.role_name
  policy = var.topic_publish_policy
}

data "aws_iam_policy_document" "read_secrets" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
    ]

    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/es_host*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/read_only/es_username*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/read_only/es_password*",
    ]
  }
}

resource "aws_iam_role_policy" "read_secrets" {
  role   = module.lambda.role_name
  policy = data.aws_iam_policy_document.read_secrets.json
}
