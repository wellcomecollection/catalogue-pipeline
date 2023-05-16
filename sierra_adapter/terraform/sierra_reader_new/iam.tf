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