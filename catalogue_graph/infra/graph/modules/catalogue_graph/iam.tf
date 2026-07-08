# Read-only access to the bulk load S3 bucket, scoped to the graph-specific prefix.
# When graph_date is empty, falls back to the "graph-prod" prefix.
# This fallback is temporary and can be removed once we retire the current production cluster.
locals {
  bulk_load_prefix = var.graph_date != "" ? "graph-${var.graph_date}" : "graph-prod"
}

data "aws_iam_policy_document" "neptune_s3_read_only_policy" {
  statement {
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = [
      "${data.aws_s3_bucket.bulk_loader_bucket.arn}/${local.bulk_load_prefix}/*"
    ]
  }

  statement {
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [
      data.aws_s3_bucket.bulk_loader_bucket.arn
    ]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${local.bulk_load_prefix}/*"]
    }
  }
}

# Neptune uses RDS for some operations
resource "aws_iam_role" "catalogue_graph_cluster" {
  name = "${local.full_namespace}-cluster"

  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = {
          Service = "rds.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}
