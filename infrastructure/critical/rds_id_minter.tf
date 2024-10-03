data "aws_ssm_parameter" "rds_username" {
  name = "/aws/reference/secretsmanager/catalogue/id_minter/rds_user"
}

data "aws_ssm_parameter" "rds_password" {
  name = "/aws/reference/secretsmanager/catalogue/id_minter/rds_password"
}

locals {
  rds_username = data.aws_ssm_parameter.rds_username.value
  rds_password = data.aws_ssm_parameter.rds_password.value
}

resource "aws_db_subnet_group" "default" {
  subnet_ids = local.private_subnets_new
}

resource "aws_security_group" "database_sg" {
  description = "Allows connection to RDS instance via TCP and egress to the world"
  vpc_id      = local.vpc_id_new
  name        = "identifiers_database_sg"

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

module "identifiers_delta_rds_cluster" {
  source             = "./modules/rds"
  cluster_identifier = "identifiers-delta-cluster"
  database_name      = "identifiers"
  username           = local.rds_username
  password           = local.rds_password

  # This instance class provides us with 45 connections per instance.
  instance_count = 1
  instance_class = "db.t3.medium"

  db_security_group_id     = aws_security_group.database_sg.id
  aws_db_subnet_group_name = aws_db_subnet_group.default.name

  engine                  = "aurora-mysql"
  db_parameter_group_name = "default.aurora-mysql5.7"
}

resource "aws_security_group" "rds_ingress_security_group" {
  name        = "pipeline_rds_ingress_security_group"
  description = "Allow traffic to rds database"
  vpc_id      = local.vpc_id_new

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  tags = {
    Name = "pipeline-rds-access"
  }
}

