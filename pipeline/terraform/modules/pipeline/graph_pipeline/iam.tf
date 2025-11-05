data "aws_caller_identity" "current" {}

locals {
  account_id             = data.aws_caller_identity.current.account_id
  secrets_manager_prefix = "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret"
}

data "aws_iam_policy_document" "allow_catalogue_graph_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "${local.secrets_manager_prefix}:catalogue-graph/*"
    ]
  }
}

data "aws_iam_policy_document" "allow_slack_secret_read" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
    ]

    resources = [
      "${local.secrets_manager_prefix}:${local.slack_webhook}*",
    ]
  }
}

data "aws_iam_policy_document" "ingestor_allow_pipeline_storage_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "${local.secrets_manager_prefix}:${var.es_cluster_host}*",
      "${local.secrets_manager_prefix}:${var.es_cluster_port}*",
      "${local.secrets_manager_prefix}:${var.es_cluster_protocol}*",
      "${local.secrets_manager_prefix}:${var.es_secrets.concepts_ingestor}*",
      "${local.secrets_manager_prefix}:${var.es_secrets.works_ingestor}*",
      "${local.secrets_manager_prefix}:${var.es_secrets.graph_extractor}*"
    ]
  }
}

data "aws_iam_policy_document" "allow_pipeline_storage_secret_read_denormalised_read_only" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "${local.secrets_manager_prefix}:${var.es_cluster_host}*",
      "${local.secrets_manager_prefix}:${var.es_cluster_port}*",
      "${local.secrets_manager_prefix}:${var.es_cluster_protocol}*",
      "${local.secrets_manager_prefix}:${var.es_secrets.graph_extractor}*"
    ]
  }
}

data "aws_iam_policy_document" "neptune_read" {
  statement {
    actions = [
      "neptune-db:Read*",
      "neptune-db:Get*",
      "neptune-db:List*"
    ]

    resources = [
      data.terraform_remote_state.catalogue_graph.outputs.neptune_cluster_data_access_arn
    ]
  }
}

data "aws_iam_policy_document" "neptune_delete" {
  statement {
    actions = [
      "neptune-db:Delete*"
    ]

    resources = [
      data.terraform_remote_state.catalogue_graph.outputs.neptune_cluster_data_access_arn
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
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}/ingestor*"
    ]
  }
  
  statement {
    actions = [
      "s3:ListBucket",
    ]

    resources = [
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}"
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
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}/graph_remover/*"
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
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}/ingestor*"
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
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}/graph_bulk_loader/*"
    ]
  }
}

data "aws_iam_policy_document" "s3_bulk_load_write" {
  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}/graph_bulk_loader/*"
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
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}/graph_remover_incremental/*"
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


