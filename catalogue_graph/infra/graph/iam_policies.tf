data "aws_iam_policy_document" "allow_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:${local.namespace}/*"
    ]
  }
}

data "aws_iam_policy_document" "allow_slack_secret_read" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
    ]

    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:${local.slack_webhook}*",
    ]
  }
}

data "aws_iam_policy_document" "ingestor_allow_pipeline_storage_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/private_host*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/port*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/protocol*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/concepts_ingestor/api_key*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/works_ingestor/api_key*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/graph_extractor/api_key*"
    ]
  }
}

data "aws_iam_policy_document" "allow_pipeline_storage_secret_read_denormalised_read_only" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/private_host*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/port*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/protocol*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/graph_extractor/api_key*"
    ]
  }
}

locals {
  account_id          = data.aws_caller_identity.current.account_id
  cluster_resource_id = aws_neptune_cluster.catalogue_graph_cluster.cluster_resource_id
  # See https://docs.aws.amazon.com/neptune/latest/userguide/iam-data-resources.html
  cluster_data_access_arn = "arn:aws:neptune-db:eu-west-1:${local.account_id}:${local.cluster_resource_id}/*"
}

# neptune read policy
data "aws_iam_policy_document" "neptune_read" {
  statement {
    actions = [
      "neptune-db:Read*",
      "neptune-db:Get*",
      "neptune-db:List*"
    ]

    resources = [
      local.cluster_data_access_arn
    ]
  }
}

# neptune delete policy
data "aws_iam_policy_document" "neptune_delete" {
  statement {
    actions = [
      "neptune-db:Delete*"
    ]

    resources = [
      local.cluster_data_access_arn
    ]
  }
}

# read from catalogue_graph_bucket s3 bucket with prefix /ingestor
data "aws_iam_policy_document" "ingestor_s3_read" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject",
      "s3:HeadObject",
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/ingestor*"
    ]
  }
}

# read from catalogue_graph_bucket s3 bucket with prefix /graph_remover
data "aws_iam_policy_document" "graph_remover_s3_read" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:GetObject",
      "s3:HeadObject",
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/graph_remover/*"
    ]
  }
}

# write in catalogue_graph_bucket s3 bucket with prefix /ingestor
data "aws_iam_policy_document" "ingestor_s3_write" {
  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/ingestor*"
    ]
  }
}

data "aws_iam_policy_document" "s3_bulk_load_read" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:HeadObject",
      "s3:GetObject"
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/graph_bulk_loader/*"
    ]
  }
}

data "aws_iam_policy_document" "s3_bulk_load_write" {
  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/graph_bulk_loader/*"
    ]
  }
}

data "aws_iam_policy_document" "ingestor_deletions_s3_policy" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:HeadObject",
      "s3:GetObject"
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/graph_remover_incremental/*"
    ]
  }
}

# write cloudwatch metrics to the "catalogue_graph_ingestor" namespace
data "aws_iam_policy_document" "cloudwatch_write" {
  statement {
    actions = [
      "cloudwatch:PutMetricData"
    ]

    resources = [
      "*"
    ]
  }
}
