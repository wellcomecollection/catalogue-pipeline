output "rds_cluster_id" {
  value = aws_rds_cluster.serverless.id
}

output "rds_cluster_arn" {
  value = aws_rds_cluster.serverless.arn
}

locals {
  manage_password = var.manage_master_user_password != null ? var.manage_master_user_password : false
  master_user_secret_arn = local.manage_password ? aws_rds_cluster.serverless.master_user_secret[0].secret_arn : null
}

output "master_user_secret_arn" {
  value = local.master_user_secret_arn
}