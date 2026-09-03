locals {
  identifiers_api_read_count = length(var.data_api_consumer_role_arns) > 0 ? 1 : 0
}

resource "aws_iam_role" "identifiers_api_read" {
  count = local.identifiers_api_read_count

  name               = "identifiers-api-registry-read${local.hyphen_suffix}"
  assume_role_policy = data.aws_iam_policy_document.identifiers_api_assume[0].json
}

data "aws_iam_policy_document" "identifiers_api_assume" {
  count = local.identifiers_api_read_count

  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "AWS"
      identifiers = var.data_api_consumer_role_arns
    }
  }
}

resource "aws_iam_role_policy" "identifiers_api_read" {
  count = local.identifiers_api_read_count

  role   = aws_iam_role.identifiers_api_read[0].name
  policy = data.aws_iam_policy_document.identifiers_api_read[0].json
}

data "aws_iam_policy_document" "identifiers_api_read" {
  count = local.identifiers_api_read_count

  statement {
    actions   = ["rds-data:ExecuteStatement"]
    resources = [module.identifiers_v2_serverless_rds_cluster.rds_cluster_arn]
  }

  # The Data API reads the credential on the caller's behalf, so the caller needs
  # access to the secret as well as to the cluster.
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [module.identifiers_v2_serverless_rds_cluster.master_user_secret_arn]
  }
}
