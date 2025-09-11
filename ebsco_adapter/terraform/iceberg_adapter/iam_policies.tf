#############################
# S3 Tables (Iceberg) access #
#############################
# Updated to use native s3tables IAM actions & ARNs instead of Glue/LakeFormation integration.
# Bucket & table names derived from previous Glue ARNs:
#   Bucket: wellcomecollection-platform-ebsco-adapter
#   Namespace: wellcomecollection_catalogue (not directly encoded in ARNs, but kept for context)
#   Table: ebsco_adapter_table

# Write policy (create/update table + read/write data & metadata)
data "aws_iam_policy_document" "iceberg_write" {
  # Bucket-level operations needed for managing (creating/listing) namespaces & tables
  statement {
    actions = [
      "s3tables:CreateNamespace",
      "s3tables:GetNamespace",
      "s3tables:ListNamespaces",
      "s3tables:CreateTable",
      "s3tables:ListTables",
      "s3tables:GetTableBucket",
      "s3tables:GetTableMetadataLocation",
    ]
    resources = [
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter",
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter/*"
    ]
  }

  # Table-level operations for reading & writing Iceberg (metadata + data files commits)
  statement {
    actions = [
      "s3tables:GetTableMetadataLocation",
      "s3tables:ListTables",
      "s3tables:GetTable",
      "s3tables:GetTableData",
      "s3tables:PutTableData",
      "s3tables:UpdateTableMetadataLocation"
    ]

    resources = [
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter/table/*"
    ]
  }
}

# Read-only policy (no mutations): list & read table and data objects
data "aws_iam_policy_document" "iceberg_read" {
  statement {
    actions = [
      "s3tables:GetNamespace",
      "s3tables:ListNamespaces",
      "s3tables:ListTables",
      "s3tables:GetTableBucket",
      "s3tables:GetTableMetadataLocation",
    ]
    resources = [
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter",
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter/*"
    ]
  }

  statement {
    actions = [
      "s3tables:GetTableMetadataLocation",
      "s3tables:ListTables",
      "s3tables:GetTable",
      "s3tables:GetTableData",
      "s3tables:UpdateTableMetadataLocation"
    ]
    resources = [
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter/table/*"
    ]
  }
}

# Policy for reading from the EBSCO adapter S3 bucket
data "aws_iam_policy_document" "s3_read" {
  statement {
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]

    resources = [
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter",
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter/*"
    ]
  }
}

# Policy for writing to the EBSCO adapter S3 bucket
data "aws_iam_policy_document" "s3_write" {
  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter",
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter/prod/ftp_v2/*",
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter/prod/batches/*"
    ]
  }
}

# Allow read ssm parameters
data "aws_iam_policy_document" "ssm_read" {
  statement {
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath"
    ]

    resources = [
      "arn:aws:ssm:eu-west-1:760097843905:parameter/catalogue_pipeline/ebsco_adapter/*"
    ]
  }

  # KMS permissions needed for WithDecryption=True on SecureString parameters
  statement {
    actions = [
      "kms:Decrypt"
    ]

    resources = [
      "arn:aws:kms:eu-west-1:760097843905:key/*"
    ]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.eu-west-1.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "transformer_allow_pipeline_storage_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/private_host*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/port*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/protocol*",
      # This should be just "transformer" once the pipeline secrets are properly generated
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/transformer-ebsco-test/api_key*"
    ]
  }
}
