# The Lambda execution role (and basic-execution logging) is provided by the
# terraform-aws-lambda module; these policies are attached to that role
# (module.sync_lambda.lambda_role), matching the adapter trigger lambdas.

locals {
  s3tables_bucket_arn  = trimsuffix(var.s3_table_bucket_arn, "/")
  s3tables_bucket_name = split("/", local.s3tables_bucket_arn)[1]
  s3_bucket_arn        = "arn:aws:s3:::${local.s3tables_bucket_name}"
}

# SSM: read the OKAPI credentials SecureString and decrypt with the default SSM KMS key.
data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

data "aws_iam_policy_document" "sync_ssm_read" {
  statement {
    effect    = "Allow"
    actions   = ["ssm:GetParameter"]
    resources = [aws_ssm_parameter.okapi_credentials.arn]
  }
  statement {
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm.target_key_arn]
  }
}

resource "aws_iam_role_policy" "sync_ssm_read" {
  name   = "${var.namespace}-ssm-read"
  role   = module.sync_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.sync_ssm_read.json
}

# CloudWatch: put custom metrics scoped to the AxiellFolioSync namespace.
data "aws_iam_policy_document" "sync_cloudwatch" {
  statement {
    effect    = "Allow"
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = ["AxiellFolioSync"]
    }
  }
}

resource "aws_iam_role_policy" "sync_cloudwatch" {
  name   = "${var.namespace}-cloudwatch"
  role   = module.sync_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.sync_cloudwatch.json
}

# S3 Tables: read Iceberg metadata and parquet data files from the managed bucket.
data "aws_iam_policy_document" "sync_s3tables" {
  statement {
    effect    = "Allow"
    actions   = ["s3tables:ListBuckets"]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3tables:GetTableBucket",
      "s3tables:GetTable",
      "s3tables:GetTableMetadata",
      "s3tables:GetTableMetadataLocation",
      "s3tables:GetTableData",
      "s3tables:GetObject",
      "s3tables:ListNamespaces",
      "s3tables:ListTables",
    ]
    resources = [
      local.s3tables_bucket_arn,
      "${local.s3tables_bucket_arn}/*",
    ]
  }

  # pyiceberg (PyArrow FileIO) reads parquet files directly from the S3-managed bucket.
  statement {
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [local.s3_bucket_arn]
  }

  statement {
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${local.s3_bucket_arn}/*"]
  }
}

resource "aws_iam_role_policy" "sync_s3tables" {
  name   = "${var.namespace}-s3tables"
  role   = module.sync_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.sync_s3tables.json
}

# S3: write manifest files to the manifest bucket.
data "aws_iam_policy_document" "sync_s3_manifests" {
  statement {
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:PutObjectTagging",
      "s3:GetObjectVersion",
    ]
    resources = [
      "${aws_s3_bucket.axiell_folio_sync_manifests.arn}/manifests/*",
    ]
  }
}

resource "aws_iam_role_policy" "sync_s3_manifests" {
  name   = "${var.namespace}-s3-manifests"
  role   = module.sync_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.sync_s3_manifests.json
}

# No ECR policy is needed here: the Lambda service pulls the same-account
# unified_pipeline_lambda image at deploy time, not via the execution role
# (matching the adapter trigger lambdas).
