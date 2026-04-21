data "aws_ssm_parameter" "rds_username" {
  name = "/aws/reference/secretsmanager/catalogue/id_minter/rds_user"
}

module "id_minter_rds" {
  source = "./modules/id-minter-rds"

  vpc_id             = local.vpc_id_new
  private_subnet_ids = local.private_subnets_new
  admin_cidr_ingress = local.admin_cidr_ingress

  master_username = data.aws_ssm_parameter.rds_username.value
}

moved {
  from = aws_db_subnet_group.default
  to   = module.id_minter_rds.aws_db_subnet_group.default
}

moved {
  from = aws_security_group.database_v2_sg
  to   = module.id_minter_rds.aws_security_group.database_v2_sg
}

moved {
  from = aws_security_group.rds_v2_ingress_security_group
  to   = module.id_minter_rds.aws_security_group.rds_v2_ingress_security_group
}

moved {
  from = module.identifiers_v2_serverless_rds_cluster
  to   = module.id_minter_rds.module.identifiers_v2_serverless_rds_cluster
}


