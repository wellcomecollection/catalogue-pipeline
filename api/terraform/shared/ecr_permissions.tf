# We need to grant cross-account permissions for these ECR repositories because:
#
#   - We publish Docker images into the platform account
#   - We run the Catalogue API services in the catalogue account
#
# Eventually we should consolidate all the catalogue services in the
# catalogue account, including publishing new images there, and then we can
# remove these permissions.

# NOTE: These roles are hard-coded rather than exported from the task modules
# because our terraform modules don't expose the execution role.  Adding extra
# permissions to it like this is an antipattern that those modules don't support.

locals {
  service_repositories = [
    "api",
    "nginx_api-gw",
    "snapshot_generator",
    "update_api_docs",
  ]
}

data "aws_iam_role" "catalogue_api_prod_execution_role" {
  name = "catalogue_api-prod_execution_role"
}

data "aws_iam_role" "catalogue_api_staging_execution_role" {
  name = "catalogue_api-staging_execution_role"
}

data "aws_iam_role" "snapshot_generator_execution_role" {
  name = "snapshot_generator_execution_role"
}

data "aws_iam_role" "update_api_docs_execution_role" {
  name = "update_api_docs_execution_role"
}

data "aws_iam_policy_document" "allow_catalogue_access" {
  statement {
    principals {
      identifiers = [
        "${data.aws_iam_role.catalogue_api_prod_execution_role.arn}",
        "${data.aws_iam_role.catalogue_api_staging_execution_role.arn}",
        "${data.aws_iam_role.snapshot_generator_execution_role.arn}",
        "${data.aws_iam_role.update_api_docs_execution_role.arn}",
      ]

      type = "AWS"
    }

    actions = [
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:BatchCheckLayerAvailability",
    ]
  }
}

resource "aws_ecr_repository_policy" "catalogue_access_policy" {
  provider   = "aws.platform"
  count      = "${length(local.service_repositories)}"
  repository = "uk.ac.wellcome/${local.service_repositories[count.index]}"
  policy     = "${data.aws_iam_policy_document.allow_catalogue_access.json}"
}
