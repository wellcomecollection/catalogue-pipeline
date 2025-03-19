data "aws_iam_policy_document" "allow_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:${local.namespace}/*"
    ]
  }
}

data "aws_iam_policy_document" "allow_pipeline_storage_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/private_host*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/port*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/protocol*",
      "arn:aws:secretsmanager:eu-west-1:${local.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/concept_ingestor/api_key*"
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

# read from catalogue_graph_bucket s3 bucket
data "aws_iam_policy_document" "ingestor_s3_read" {
  statement {
    actions = [
      "s3:GetObject",
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/ingestor/*"
    ]
  }
}

# write in catalogue_graph_bucket s3 bucket with prefix /ingestor
data "aws_iam_policy_document" "ingestor_s3_write" {
  statement {
    actions = [
      "s3:PutObject"
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/ingestor/*"
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
