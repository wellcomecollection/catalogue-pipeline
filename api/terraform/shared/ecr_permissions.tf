# We need to grant cross-account permissions for these ECR repositories because:
#
#   - We publish Docker images into the platform account
#   - We run the Catalogue API services in the catalogue account
#
# Eventually we should consolidate all the catalogue services in the
# catalogue account, including publishing new images there, and then we can
# remove these permissions.

locals {
  service_repositories = [
    "api",
    "nginx_api-gw",
    "snapshot_generator",
    "update_api_docs",
  ]
}

data "aws_iam_policy_document" "allow_catalogue_access" {
  statement {
    principals {
      identifiers = [
        "arn:aws:iam::756629837203:root"
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
  provider = aws.platform

  count      = length(local.service_repositories)
  repository = "uk.ac.wellcome/${local.service_repositories[count.index]}"
  policy     = data.aws_iam_policy_document.allow_catalogue_access.json
}
