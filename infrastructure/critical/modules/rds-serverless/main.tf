resource "aws_rds_cluster" "serverless" {
  cluster_identifier = var.cluster_identifier
  database_name      = var.database_name
  master_username    = var.master_username
  master_password    = var.master_password

  snapshot_identifier = var.snapshot_identifier

  engine         = "aurora-mysql"
  engine_mode    = "provisioned"
  engine_version = var.engine_version

  db_subnet_group_name   = var.aws_db_subnet_group_name
  vpc_security_group_ids = [var.db_security_group_id]

  storage_encrypted    = false
  // This needs to be false until migration is complete
  enable_http_endpoint = true

  serverlessv2_scaling_configuration {
    max_capacity = var.max_scaling_capacity
    min_capacity = var.min_scaling_capacity
  }
}

// Note when migrating we will need to:
// - comment out the serverless cluster instance
// - create a new serverless cluster instance with type "db.t3.medium"
// - when complete uncomment the serverless cluster instance
// - manual failover to the new serverless cluster
// - delete the old serverless cluster instance

# resource "aws_rds_cluster_instance" "migration_instance" {
#   cluster_identifier = aws_rds_cluster.serverless.id
#   instance_class     = "db.t3.medium"
#   engine             = aws_rds_cluster.serverless.engine
#   engine_version     = aws_rds_cluster.serverless.engine_version
# }

resource "aws_rds_cluster_instance" "serverless_instance" {
  cluster_identifier = aws_rds_cluster.serverless.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.serverless.engine
  engine_version     = aws_rds_cluster.serverless.engine_version
}

