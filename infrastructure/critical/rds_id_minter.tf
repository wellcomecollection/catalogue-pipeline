data "aws_ssm_parameter" "rds_username" {
  name = "/aws/reference/secretsmanager/catalogue/id_minter/rds_user"
}

module "id_minter_rds" {
  source = "./modules/id-minter-rds"

  vpc_id             = local.vpc_id_new
  private_subnet_ids = local.private_subnets_new
  admin_cidr_ingress = local.admin_cidr_ingress
  engine_version     = "8.0.mysql_aurora.3.10.3"

  master_username = data.aws_ssm_parameter.rds_username.value
}

module "id_minter_rds_2026_07_03" {
  source = "./modules/id-minter-rds"

  name_suffix = "2026-07-03"
  # Restore from production on July 30, 2026, 04:00 (UTC+01:00)
  snapshot_identifier = "awsbackup:job-23667bdb-6b2b-92c9-d653-03f01ced06ce"

  # A restored copy of production; its contents are never worth keeping.
  skip_final_snapshot = true

  vpc_id             = local.vpc_id_new
  private_subnet_ids = local.private_subnets_new
  admin_cidr_ingress = local.admin_cidr_ingress
  engine_version     = "8.0.mysql_aurora.3.10.3"

  max_scaling_capacity = 32

  master_username = data.aws_ssm_parameter.rds_username.value
}
