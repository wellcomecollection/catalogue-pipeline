locals {
  secret_env_vars = {
    for k, v in var.secret_env_vars
    : k => "secret:${v}"
  }

  # Strip any :json_key suffix from secret names.
  # This supports the secret:name:json_key syntax used by the Python
  # secrets extension to extract a key from a JSON secret.
  secret_base_names = distinct([
    for v in values(var.secret_env_vars)
    : split(":", v)[0]
  ])

  environment_variables_with_secrets = merge(var.environment_variables, local.secret_env_vars)
}

data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

# data block for aws_iam_policy_document to read each secret in secret_names (presuming they are in the same account as the lambda)
data "aws_iam_policy_document" "secrets_policy" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      for secret_name in local.secret_base_names
      : "arn:aws:secretsmanager:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:secret:${secret_name}-*"
    ]
  }
}

resource "aws_iam_policy" "secret_policy" {
  count = length(local.secret_base_names) > 0 ? 1 : 0

  name_prefix = "${local.name}-secrets-policy"
  policy      = data.aws_iam_policy_document.secrets_policy.json
}

resource "aws_iam_role_policy_attachment" "lambda_secret_role_policy" {
  count = length(local.secret_base_names) > 0 ? 1 : 0

  role       = module.pipeline_step.lambda_role.name
  policy_arn = aws_iam_policy.secret_policy[0].arn
}
