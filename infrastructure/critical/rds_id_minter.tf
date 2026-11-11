data "aws_ssm_parameter" "rds_username" {
  name = "/aws/reference/secretsmanager/catalogue/id_minter/rds_user"
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

  # Both Identifiers API environments read this registry.
  data_api_consumer_role_arns = [
    "arn:aws:iam::756629837203:role/lambda-role-identifiers-api-stage",
    "arn:aws:iam::756629837203:role/lambda-role-identifiers-api-prod",
  ]
}

module "id_minter_rds_2026_10_20" {
  source = "./modules/id-minter-rds"

  name_suffix = "2026-10-20"
  # Restored from production's November 10, 2026, 03:22 backup so FOLIO records can
  # inherit their Sierra predecessors' canonical ids.
  snapshot_identifier = "awsbackup:job-d47977c6-e715-45d4-49dd-ba374077f60b"

  # The FOLIO environment's registry, a copy of production that never becomes it.
  skip_final_snapshot = true

  # Read through the Identifiers API's idDatabase alias, so the toggled FOLIO
  # flows see it while unaliased requests stay on production (wellcomecollection/platform#6658).
  data_api_consumer_role_arns = [
    "arn:aws:iam::756629837203:role/lambda-role-identifiers-api-stage",
    "arn:aws:iam::756629837203:role/lambda-role-identifiers-api-prod",
  ]

  vpc_id             = local.vpc_id_new
  private_subnet_ids = local.private_subnets_new
  admin_cidr_ingress = local.admin_cidr_ingress
  engine_version     = "8.0.mysql_aurora.3.10.3"

  master_username = data.aws_ssm_parameter.rds_username.value
}
