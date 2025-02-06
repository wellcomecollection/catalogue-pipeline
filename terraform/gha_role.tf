module "gha_catalogue_graph_ci_role" {
  source = "github.com/wellcomecollection/terraform-aws-gha-role?ref=v1.0.0"

  policy_document           = data.aws_iam_policy_document.gha_catalogue_graph_ci.json
  github_repository         = "wellcomecollection/catalogue-graph"
  role_name                 = "catalogue-graph-ci"
  github_oidc_provider_arn  = data.terraform_remote_state.aws_account_infrastructure.outputs.github_openid_connect_provider_arn
}

data "aws_iam_policy_document" "gha_catalogue_graph_ci" {
  statement {
    actions   = [
      "s3:PutObject"
    ]
    resources = [
      "arn:aws:s3:::wellcomecollection-platform-infra/lambdas/catalogue_graph/*"
    ]
  }
  statement {
    actions   = [
      "ecr:BatchCheckLayerAvailability",
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
      "arn:aws:ecr:eu-west-1:760097843905:repository/uk.ac.wellcome/catalogue_graph_extractor"
    ]
  }
  statement {
    actions   = [
      "lambda:GetFunctionConfiguration",
      "lambda:UpdateFunctionCode"
    ]
    resources = [
      "arn:aws:lambda:eu-west-1:760097843905:function:catalogue-graph-extractor",
      "arn:aws:lambda:eu-west-1:760097843905:function:catalogue-graph-bulk-loader",
      "arn:aws:lambda:eu-west-1:760097843905:function:catalogue-graph-bulk-load-poller",
      "arn:aws:lambda:eu-west-1:760097843905:function:catalogue-graph-indexer"
    ]
  }
}

output "gha_catalogue_graph_ci_role_arn" {
  value = module.gha_catalogue_graph_ci_role.outputs.role_arn
}