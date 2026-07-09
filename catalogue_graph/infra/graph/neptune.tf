module "catalogue_graph_neptune_cluster" {
  source = "./modules/catalogue_graph"

  # This is the current production cluster, which was created before we introduced graph dates.
  # It has an empty graph_date to preserve its Neptune cluster name (catalogue-graph),
  # otherwise Terraform would destroy it (Neptune cluster names cannot be changed).
  # Eventually, we will switch to a new (dated) production cluster, at which point
  # we can destroy this one and make graph dates mandatory.
  graph_date                 = ""
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

resource "aws_ssm_parameter" "production_graph_date" {
  name        = "/catalogue_graph/production_graph_date"
  type        = "String"
  description = "The graph_date of the current production Neptune cluster (or 'prod' for the legacy cluster), read by CI."
}
