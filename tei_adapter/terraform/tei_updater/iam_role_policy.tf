
resource "aws_iam_role_policy" "tei_updater_policy" {
  role   = module.tei_updater_lambda.role_name
  policy = data.aws_iam_policy_document.publish_to_adapter_topic.json
}

resource "aws_iam_role_policy" "tei_updater_s3_read_write" {
  role   = module.tei_updater_lambda.role_name
  policy = data.aws_iam_policy_document.allow_s3_read_write.json
}

resource "aws_iam_role_policy" "tei_updater_read_github_token_secret" {
  role   = module.tei_updater_lambda.role_name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

data "aws_iam_policy_document" "allow_secret_read" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = ["${var.github_token_secret}*"]
  }
}
