module "gha_catalogue_graph_ci_role" {
  source = "github.com/wellcomecollection/terraform-aws-gha-role?ref=v1.0.0"

  policy_document          = data.aws_iam_policy_document.gha_catalogue_graph_ci.json
  github_repository        = "wellcomecollection/catalogue-pipeline"
  role_name                = "catalogue-graph-ci"
  github_oidc_provider_arn = data.terraform_remote_state.aws_account_infrastructure.outputs.github_openid_connect_provider_arn
}

data "aws_iam_policy_document" "gha_catalogue_graph_ci" {
  statement {
    actions = [
      "s3:PutObject",
      "s3:GetObject"
    ]
    resources = [
      "arn:aws:s3:::wellcomecollection-platform-infra/lambdas/catalogue_graph",
      "arn:aws:s3:::wellcomecollection-platform-infra/lambdas/catalogue_graph/*"
    ]
  }
  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:Describe*",
      "ecr:Get*",
      "ecr:List*",
      "ecr:TagResource",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
    ]
    resources = [
      "arn:aws:ecr:eu-west-1:760097843905:repository/uk.ac.wellcome/catalogue_graph_extractor",
      "arn:aws:ecr:eu-west-1:760097843905:repository/uk.ac.wellcome/unified_pipeline_lambda"
    ]
  }

  statement {
    actions = [
      "ecr:GetAuthorizationToken"
    ]
    resources = [
      "*"
    ]
  }
  statement {
    actions = [
      "lambda:GetFunctionConfiguration",
      "lambda:UpdateFunctionCode"
    ]
    resources = [
      "arn:aws:lambda:eu-west-1:760097843905:function:catalogue-*",
    ]
  }
}
