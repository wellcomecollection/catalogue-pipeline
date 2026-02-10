# RDS

output "rds_serverless_cluster_id" {
  value = module.identifiers_serverless_rds_cluster.rds_cluster_id
}

output "rds_v2_serverless_cluster_id" {
  value = module.identifiers_v2_serverless_rds_cluster.rds_cluster_id
}

output "rds_v2_serverless_cluster_arn" {
  value = module.identifiers_v2_serverless_rds_cluster.rds_cluster_arn
}

output "rds_v2_access_security_group_id" {
  value = aws_security_group.rds_v2_ingress_security_group.id
}

output "rds_subnet_group_name" {
  value = aws_db_subnet_group.default.name
}

output "rds_access_security_group_id" {
  value = aws_security_group.rds_ingress_security_group.id
}

# Miro Hybrid Store

output "vhs_miro_read_policy" {
  value = module.vhs_miro.read_policy
}

output "vhs_miro_table_name" {
  value = module.vhs_miro.table_name
}

output "vhs_miro_assumable_read_role" {
  value = module.vhs_miro.assumable_read_role
}
