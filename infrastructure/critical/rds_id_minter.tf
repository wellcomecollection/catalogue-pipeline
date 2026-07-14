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

module "id_minter_rds_test" {
  source = "./modules/id-minter-rds"

  name_suffix         = "test"
  # Restore from production on April 21, 2026, 04:18 (UTC+01:00)
  snapshot_identifier = "awsbackup:job-349affad-75e5-83a7-5e9b-7c631bdfa39e"

  vpc_id             = local.vpc_id_new
  private_subnet_ids = local.private_subnets_new
  admin_cidr_ingress = local.admin_cidr_ingress
  engine_version     = "8.0.mysql_aurora.3.10.3"

  master_username = data.aws_ssm_parameter.rds_username.value
}

module "id_minter_rds_2026_07_03" {
  source = "./modules/id-minter-rds"

  name_suffix         = "2026-07-03"
  # Restore from production on July 14, 2026, 04:41 (UTC+01:00)
  snapshot_identifier = "awsbackup:job-a03d7ad1-17f3-4f12-6993-1c7a19cda33d"

  vpc_id             = local.vpc_id_new
  private_subnet_ids = local.private_subnets_new
  admin_cidr_ingress = local.admin_cidr_ingress
  engine_version     = "8.0.mysql_aurora.3.10.3"

  master_username = data.aws_ssm_parameter.rds_username.value
}
