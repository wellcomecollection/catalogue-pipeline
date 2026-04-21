resource "aws_db_subnet_group" "default" {
  subnet_ids = var.private_subnet_ids
}

resource "aws_security_group" "database_v2_sg" {
  description = "Allows connection to identifiers-v2 RDS instance via TCP and egress to the world"
  vpc_id      = var.vpc_id
  name        = "identifiers_v2_database_sg"

  ingress {
    protocol  = "tcp"
    from_port = 3306
    to_port   = 3306

    cidr_blocks = [
      var.admin_cidr_ingress,
    ]
  }

  ingress {
    from_port = 3306
    to_port   = 3306
    protocol  = "tcp"

    security_groups = [aws_security_group.rds_v2_ingress_security_group.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

module "identifiers_v2_serverless_rds_cluster" {
  source = "../rds-serverless"

  cluster_identifier = "identifiers-v2-serverless"
  database_name      = "identifiers"
  master_username    = var.master_username
  master_password    = null

  manage_master_user_password = true

  db_security_group_id     = aws_security_group.database_v2_sg.id
  aws_db_subnet_group_name = aws_db_subnet_group.default.name

  max_scaling_capacity = var.max_scaling_capacity

  engine_version = var.engine_version
}

resource "aws_security_group" "rds_v2_ingress_security_group" {
  name        = "pipeline_rds_v2_ingress_security_group"
  description = "Allow traffic to identifiers-v2 rds database"
  vpc_id      = var.vpc_id

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  tags = {
    Name = "pipeline-rds-v2-access"
  }
}
