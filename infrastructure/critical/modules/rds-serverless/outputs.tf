output "rds_cluster_id" {
  value = aws_rds_cluster.serverless.id
}

output "rds_cluster_arn" {
  value = aws_rds_cluster.serverless.arn
}
