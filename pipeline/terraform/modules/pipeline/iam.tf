data "aws_iam_policy_document" "read_ebsco_adapter_bucket" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject*",
    ]

    resources = [
      "arn:aws:s3:::${local.ebsco_adapter_bucket}",
      "arn:aws:s3:::${local.ebsco_adapter_bucket}/*",
    ]
  }
}

data "aws_iam_policy_document" "read_ebsco_adapter_s3tables_bucket" {
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

data "aws_iam_policy_document" "read_ebsco_transformer_pipeline_storage_secrets" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/private_host*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/port*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/protocol*",
<<<<<<< HEAD
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/transformer/api_key*"
=======
      # TODO: This is a manually created secret for testing the new transformer, it needs to be automated!
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:elasticsearch/pipeline_storage_${var.pipeline_date}/transformer-ebsco-test/api_key*"
>>>>>>> 50f36cce0 (allow multiple date indexes in the same pipeline)
    ]
  }
}

data "aws_iam_policy_document" "write_ebsco_adapter_bucket" {
  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "arn:aws:s3:::${local.ebsco_adapter_bucket}/prod/batches/*"
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
