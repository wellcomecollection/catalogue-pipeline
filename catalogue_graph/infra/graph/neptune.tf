module "catalogue_graph_neptune_cluster" {
  source = "./modules/catalogue_graph"

  namespace = local.namespace
  vpc_id = local.vpc_id
  private_subnets = local.private_subnets
  public_subnets = local.public_subnets
  bulk_loader_s3_bucket_name = aws_s3_bucket.catalogue_graph_bucket.bucket
  public_url = "catalogue-graph.wellcomecollection.org"

  providers = {
    aws = aws
  }
}
