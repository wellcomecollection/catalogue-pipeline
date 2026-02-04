data "aws_ssm_parameter" "rds_username" {
  name = "/aws/reference/secretsmanager/catalogue/tei_id_extractor/rds_user"
}

data "aws_ssm_parameter" "rds_password" {
  name = "/aws/reference/secretsmanager/catalogue/tei_id_extractor/rds_password"
}

locals {
  rds_username = data.aws_ssm_parameter.rds_username.value
  rds_password = data.aws_ssm_parameter.rds_password.value
}

resource "aws_db_subnet_group" "default" {
  subnet_ids = local.private_subnets
}

resource "aws_security_group" "database_sg" {
  description = "Allows connection to RDS instance via TCP and egress to the world"
  vpc_id      = local.vpc_id
  name        = "tei_id_extractor_database_sg"

  ingress {
    protocol  = "tcp"
    from_port = 3306
    to_port   = 3306

    cidr_blocks = [
      local.admin_cidr_ingress,
    ]
  }

  ingress {
    from_port = 3306
    to_port   = 3306
    protocol  = "tcp"

    security_groups = [aws_security_group.rds_ingress_security_group.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

module "tei_id_extractor_rds_serverless_cluster" {
  source             = "../../infrastructure/critical/modules/rds-serverless"
  cluster_identifier = "tei-adapter-cluster-serverless"
  database_name      = "pathid"
  master_username    = local.rds_username
  master_password    = local.rds_password

  db_security_group_id     = aws_security_group.database_sg.id
  aws_db_subnet_group_name = aws_db_subnet_group.default.name

  engine_version = "8.0.mysql_aurora.3.08.2"

  snapshot_identifier = "aurora-mysql-v3-tei-pre-migration-24-15-08"
}


resource "aws_security_group" "rds_ingress_security_group" {
  name        = "tei_adapter_rds_ingress_security_group"
  description = "Allow traffic to rds database"
  vpc_id      = local.vpc_id

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  tags = {
    Name = "tei-adapter-rds-access"
  }
}

resource "aws_db_parameter_group" "default" {
  name_prefix = "tei-rds"
  family      = "aurora-mysql5.7"

  parameter {
    name  = "wait_timeout"
    value = local.rds_lock_timeout_seconds
  }
}
