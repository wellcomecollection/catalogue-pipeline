# Read-only access to the bulk load S3 bucket
data "aws_iam_policy_document" "neptune_s3_read_only_policy" {
  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]
    resources = [
      data.aws_s3_bucket.bulk_loader_bucket.arn,
      "${data.aws_s3_bucket.bulk_loader_bucket.arn}/*"
    ]
  }
}

# Neptune uses RDS for some operations
resource "aws_iam_role" "catalogue_graph_cluster" {
  name = "catalogue-graph-cluster"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "rds.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}
