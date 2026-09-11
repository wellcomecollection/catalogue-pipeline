locals {
  # The sbt apps whose images publish_sbt_image_to_ecr.sh pushes, matching the
  # app matrix in .buildkite/pipeline.yml. Listed rather than wildcarded so that
  # adding an app is a deliberate change here.
  scala_app_ecr_repositories = [
    "calm_adapter",
    "calm_deletion_checker",
    "calm_indexer",
    "matcher",
    "merger",
    "mets_adapter",
    "reindex_worker",
    "sierra_indexer",
    "sierra_linker",
    "sierra_merger",
    "tei_adapter",
    "tei_id_extractor",
    "transformer_calm",
    "transformer_mets",
    "transformer_miro",
    "transformer_sierra",
    "transformer_tei",
  ]
}

module "gha_scala_ci_role" {
  source = "github.com/wellcomecollection/terraform-aws-gha-role?ref=v1.0.0"

  policy_document = data.aws_iam_policy_document.gha_scala_ci.json
  role_name       = "scala-ci"

  # Scoped to main, matching what Buildkite does today: branches build an image
  # but only main publishes one. The role can overwrite the floating 'latest'
  # tag that deploys resolve, so it should not be assumable from a branch.
  #
  # Note this only matches push events. GitHub's OIDC subject for a
  # pull_request run is "repo:<owner>/<repo>:pull_request", so pull request jobs
  # cannot assume this role at all and use the pull-only formatting role to
  # fetch the sbt_wrapper image.
  github_repository = "wellcomecollection/catalogue-pipeline:ref:refs/heads/main"

  github_oidc_provider_arn = data.terraform_remote_state.aws_account_infrastructure.outputs.github_openid_connect_provider_arn
}

data "aws_iam_policy_document" "gha_scala_ci" {
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
      for repository in local.scala_app_ecr_repositories :
      "arn:aws:ecr:eu-west-1:760097843905:repository/uk.ac.wellcome/${repository}"
    ]
  }

  # Pulling the sbt_wrapper image the build runs in.
  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [
      "arn:aws:ecr:eu-west-1:760097843905:repository/wellcome/sbt_wrapper",
    ]
  }

  # Has to be unscoped: the token is account-wide rather than per repository.
  statement {
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = [
      "*",
    ]
  }
}

resource "github_actions_secret" "scala_ci" {
  repository      = "catalogue-pipeline"
  secret_name     = "SCALA_CI_ROLE_ARN"
  plaintext_value = module.gha_scala_ci_role.role_arn
}
