module "ecr_repository_snapshot_generator" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v1.0.0"
  name   = "snapshot_generator"
}

data "aws_iam_role" "snapshot_generator_execution_role" {
  name = "${module.snapshot_generator.service_name}_execution_role"
}

data "aws_iam_policy_document" "allow_snapshot_generator_access" {
  statement {
    principals {
      identifiers = [
        "${data.aws_iam_role.snapshot_generator_execution_role.arn}",
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

resource "aws_ecr_repository_policy" "snapshot_generator_access_policy" {
  provider   = "aws.platform_account"
  repository = "uk.ac.wellcome/snapshot_generator"
  policy     = "${data.aws_iam_policy_document.allow_snapshot_generator_access.json}"
}
