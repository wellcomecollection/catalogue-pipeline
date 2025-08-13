module "gha_ebsco_adapter_ci_role" {
  source = "github.com/wellcomecollection/terraform-aws-gha-role?ref=v1.0.0"

  policy_document          = data.aws_iam_policy_document.gha_ebsco_adapter_ci.json
  github_repository        = "wellcomecollection/catalogue-pipeline"
  role_name                = "ebsco-adapter-ci"
  github_oidc_provider_arn = data.terraform_remote_state.aws_account_infrastructure.outputs.github_openid_connect_provider_arn
}

data "aws_iam_policy_document" "gha_ebsco_adapter_ci" {
  statement {
    actions = [
      "s3:PutObject",
      "s3:GetObject"
    ]
    resources = [
      "arn:aws:s3:::wellcomecollection-platform-infra/lambdas/ebsco_adapter",
      "arn:aws:s3:::wellcomecollection-platform-infra/lambdas/ebsco_adapter/*"
    ]
  }

  statement {
    actions = [
      "lambda:GetFunctionConfiguration",
      "lambda:UpdateFunctionCode"
    ]
    resources = [
      "arn:aws:lambda:eu-west-1:760097843905:function:ebsco-adapter-*",
    ]
  }
}
