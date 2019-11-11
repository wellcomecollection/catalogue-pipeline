data "aws_iam_policy_document" "allow_catalogue_access" {
  statement {
    principals {
      identifiers = [
        "${local.platform_developer_role_arn}",
        "${local.catalogue_developer_role_arn}",
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
  provider   = "aws.platform_account"
  count      = "${length(local.service_repositories)}"
  repository = "${local.service_repositories[count.index]}"
  policy     = "${data.aws_iam_policy_document.allow_catalogue_access.json}"
}
