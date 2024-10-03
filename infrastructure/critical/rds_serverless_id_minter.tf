module "identifiers_serverless_rds_cluster" {
  source             = "./modules/rds-serverless"

  cluster_identifier = "identifiers-serverless"
  database_name      = "identifiers"
  master_username    = local.rds_username

  aws_db_subnet_group_name = aws_db_subnet_group.default.name
}
