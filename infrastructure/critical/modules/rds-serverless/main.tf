resource "aws_rds_cluster" "serverless" {
  cluster_identifier          = var.cluster_identifier
  database_name               = var.database_name
  master_username             = var.master_username
  manage_master_user_password = true

  engine                      = "aurora-mysql"
  engine_mode                 = "provisioned"
  engine_version              = "8.0.mysql_aurora.3.05.2"

  db_subnet_group_name = var.aws_db_subnet_group_name

  storage_encrypted           = false

  serverlessv2_scaling_configuration {
    max_capacity = 1.0
    min_capacity = 0.5
  }
}

resource "aws_rds_cluster_instance" "serverless" {
  cluster_identifier = aws_rds_cluster.serverless.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.serverless.engine
  engine_version     = aws_rds_cluster.serverless.engine_version
}

variable "aws_db_subnet_group_name" {
  type = string
}
