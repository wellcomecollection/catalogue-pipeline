data "aws_vpc" "vpc" {
  id = var.vpc_id
}

data "aws_s3_bucket" "bulk_loader_bucket" {
  bucket = var.bulk_loader_s3_bucket_name
}

data "aws_caller_identity" "current" {}

locals {
  # See https://docs.aws.amazon.com/neptune/latest/userguide/iam-data-resources.html
  account_id              = data.aws_caller_identity.current.account_id
  cluster_resource_id     = aws_neptune_cluster.catalogue_graph_cluster.cluster_resource_id
  cluster_data_access_arn = "arn:aws:neptune-db:eu-west-1:${local.account_id}:${local.cluster_resource_id}/*"
}

