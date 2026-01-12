locals {
  adapter_buckets = {
    ebsco  = local.ebsco_adapter_bucket
    axiell = local.axiell_adapter_bucket
  }
}

data "aws_iam_policy_document" "adapter_bucket_read" {
  for_each = local.adapter_buckets

  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject*",
    ]

    resources = [
      "arn:aws:s3:::${each.value}",
      "arn:aws:s3:::${each.value}/*",
    ]
  }
}

data "aws_iam_policy_document" "adapter_s3tables_read" {
  for_each = local.adapter_buckets

  statement {
    actions = [
      "s3tables:GetNamespace",
      "s3tables:ListNamespaces",
      "s3tables:ListTables",
      "s3tables:GetTableBucket",
      "s3tables:GetTableMetadataLocation",
    ]
    resources = [
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/${each.value}",
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/${each.value}/*"
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
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/${each.value}/table/*"
    ]
  }
}

data "aws_iam_policy_document" "read_ebsco_transformer_pipeline_storage_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/private_host*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/port*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/protocol*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/transformer/api_key*"
    ]
  }
}

data "aws_iam_policy_document" "read_axiell_transformer_pipeline_storage_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/private_host*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/port*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/protocol*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/transformer_axiell/api_key*"
    ]
  }
}

data "aws_iam_policy_document" "adapter_bucket_write" {
  for_each = local.adapter_buckets

  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "arn:aws:s3:::${each.value}/prod/batches/*"
    ]
  }
}

data "aws_iam_policy_document" "read_tei_adapter_bucket" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject*",
    ]

    resources = [
      "arn:aws:s3:::${local.tei_adapter_bucket}",
      "arn:aws:s3:::${local.tei_adapter_bucket}/*",
    ]
  }
}

data "aws_iam_policy_document" "read_storage_bucket" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject*",
    ]

    resources = [
      "arn:aws:s3:::${local.storage_bucket}",
      "arn:aws:s3:::${local.storage_bucket}/*",
    ]
  }
}
