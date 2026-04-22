output "rds_cluster_id" {
  value = module.identifiers_v2_serverless_rds_cluster.rds_cluster_id
}

output "rds_cluster_arn" {
  value = module.identifiers_v2_serverless_rds_cluster.rds_cluster_arn
}

output "master_user_secret_arn" {
  value = module.identifiers_v2_serverless_rds_cluster.master_user_secret_arn
}

output "ingress_security_group_id" {
  value = aws_security_group.rds_v2_ingress_security_group.id
}

output "subnet_group_name" {
  value = aws_db_subnet_group.default.name
}
