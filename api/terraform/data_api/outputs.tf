output "snapshots_bucket_arn" {
  value = aws_s3_bucket.public_data.arn
}

output "cluster_arn" {
  value = aws_ecs_cluster.data_api.arn
}

output "cluster_name" {
  value = aws_ecs_cluster.data_api.name
}
