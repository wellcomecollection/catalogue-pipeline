# IAM Policy Documents for S3 Tables Iceberg access

data "aws_iam_policy_document" "iceberg_write" {
  statement {
    actions = [
      "lakeformation:GetDataAccess"
    ]

    resources = ["*"]
  }

  statement {
    actions = [
      "glue:GetCatalog",
      "glue:CreateDatabase",
      "glue:CreateTable",
      "glue:GetTable",
      "glue:UpdateTable"
    ]

    resources = [
      "arn:aws:glue:eu-west-1:760097843905:catalog",
      "arn:aws:glue:eu-west-1:760097843905:catalog/*",
      "arn:aws:glue:eu-west-1:760097843905:database/s3tablescatalog/wellcomecollection-platform-ebsco-adapter/wellcomecollection_catalogue"
    ]
  }

  statement {
    actions = [
      "glue:CreateTable",
      "glue:GetTable",
      "glue:UpdateTable"
    ]

    resources = [
      "arn:aws:glue:eu-west-1:760097843905:table/s3tablescatalog/wellcomecollection-platform-ebsco-adapter/wellcomecollection_catalogue/ebsco_adapter_table"
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
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter/prod/ftp_v2/*"
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
