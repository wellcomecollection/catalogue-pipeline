locals {
  neptune_clusters = [
    module.catalogue_graph_neptune_cluster_dev,
    module.catalogue_graph_neptune_cluster_2026_07_03,
    module.catalogue_graph_neptune_cluster_2026_09_30
  ]

  production_cluster = module.catalogue_graph_neptune_cluster_2026_09_30
}

module "catalogue_graph_neptune_cluster_dev" {
  source = "./modules/catalogue_graph"

  # This is a special, non-production cluster, available for experimentation.
  # It uses 'dev' as a graph date instead of a real date, following a convention we use elsewhere.
  # Its experimental/non-production status is codified in the graph pipeline, which includes a safety
  # mechanism stopping us from combining the 'dev' graph date with production ES indexes.
  graph_date                 = "dev"
  namespace                  = local.namespace
  vpc_id                     = local.vpc_id
  private_subnets            = local.private_subnets
  public_subnets             = local.public_subnets
  bulk_loader_s3_bucket_name = aws_s3_bucket.catalogue_graph_bucket.bucket

  providers = {
    aws     = aws
    aws.dns = aws.dns
  }
}

module "catalogue_graph_neptune_cluster_2026_07_03" {
  source = "./modules/catalogue_graph"

  # Previous production cluster, kept while the 2026-07-03 pipeline remains the fallback
  # (wellcomecollection/platform#6743).
  graph_date                 = "2026-07-03"
  namespace                  = local.namespace
  vpc_id                     = local.vpc_id
  private_subnets            = local.private_subnets
  public_subnets             = local.public_subnets
  bulk_loader_s3_bucket_name = aws_s3_bucket.catalogue_graph_bucket.bucket

  providers = {
    aws     = aws
    aws.dns = aws.dns
  }
}

module "catalogue_graph_neptune_cluster_2026_09_30" {
  source = "./modules/catalogue_graph"

  # The production cluster since the 2026-10-06 switch (wellcomecollection/platform#6743);
  # builds Axiell trees from the 982 parent link (wellcomecollection/platform#6725).
  graph_date                 = "2026-09-30"
  namespace                  = local.namespace
  vpc_id                     = local.vpc_id
  private_subnets            = local.private_subnets
  public_subnets             = local.public_subnets
  bulk_loader_s3_bucket_name = aws_s3_bucket.catalogue_graph_bucket.bucket

  providers = {
    aws     = aws
    aws.dns = aws.dns
  }
}

resource "aws_ssm_parameter" "production_graph_date" {
  name        = "/catalogue_graph/production_graph_date"
  type        = "String"
  description = "The graph_date of the current production Neptune cluster, read by CI."
  value       = local.production_cluster.graph_date
}
