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
  # Restore from production on August 19, 2026, 04:00 (UTC+01:00)
  snapshot_identifier = "awsbackup:job-7adec63f-19b6-5ff9-272d-f39aad1a79a8"

  # A restored copy of production, respun from a fresh snapshot each testing round,
  # so its contents are disposable for now. After switchover, the respin taken
  # inside the freeze becomes the production registry and must be kept.
  # Phase 6 of https://github.com/wellcomecollection/platform/issues/6541 removes
  # skip_final_snapshot; read it before clearing this.
  skip_final_snapshot = true

  vpc_id             = local.vpc_id_new
  private_subnet_ids = local.private_subnets_new
  admin_cidr_ingress = local.admin_cidr_ingress
  engine_version     = "8.0.mysql_aurora.3.10.3"

  max_scaling_capacity = 32

  master_username = data.aws_ssm_parameter.rds_username.value

  data_api_consumer_role_arns = [
    "arn:aws:iam::756629837203:role/lambda-role-identifiers-api-stage",
  ]
}
