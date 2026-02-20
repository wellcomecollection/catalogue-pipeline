module "catalogue_graph_neptune_cluster" {
  source = "./modules/catalogue_graph"

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

  namespace                  = "${local.namespace}-dev"
  vpc_id                     = local.vpc_id
  private_subnets            = local.private_subnets
  public_subnets             = local.public_subnets
  bulk_loader_s3_bucket_name = aws_s3_bucket.catalogue_graph_bucket.bucket

  providers = {
    aws     = aws
    aws.dns = aws.dns
  }
}
