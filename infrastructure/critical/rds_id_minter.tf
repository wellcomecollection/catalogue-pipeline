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

  data_api_consumer_role_arns = [
    "arn:aws:iam::756629837203:role/lambda-role-identifiers-api-prod",
  ]
}

module "id_minter_rds_2026_07_03" {
  source = "./modules/id-minter-rds"

  name_suffix = "2026-07-03"
  # Restore from production on September 24, 2026, 04:00 (UTC+01:00), the first
  # backup after the switchover freeze time of 2026-09-23 16:35:31 BST.
  snapshot_identifier = "awsbackup:job-e62ac7a0-482a-b8bd-2e26-5328c4479d96"

  # Production registry after the switchover (wellcomecollection/platform#6541); never drop its data.

  vpc_id             = local.vpc_id_new
  private_subnet_ids = local.private_subnets_new
  admin_cidr_ingress = local.admin_cidr_ingress
  engine_version     = "8.0.mysql_aurora.3.10.3"

  max_scaling_capacity = 32

  master_username = data.aws_ssm_parameter.rds_username.value

  # The prod role is trusted ahead of the switchover repoint
  # (wellcomecollection/catalogue-api#1008) so that cutover needs no apply here.
  data_api_consumer_role_arns = [
    "arn:aws:iam::756629837203:role/lambda-role-identifiers-api-stage",
    "arn:aws:iam::756629837203:role/lambda-role-identifiers-api-prod",
  ]
}
