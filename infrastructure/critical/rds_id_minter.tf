data "aws_ssm_parameter" "rds_username" {
  name = "/aws/reference/secretsmanager/catalogue/id_minter/rds_user"
}

data "aws_ssm_parameter" "rds_password" {
  name = "/aws/reference/secretsmanager/catalogue/id_minter/rds_password"
}

locals {
  username = data.aws_ssm_parameter.rds_username.value
  password = data.aws_ssm_parameter.rds_password.value
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

module "identifiers_serverless_rds_cluster" {
  source = "./modules/rds-serverless"

  cluster_identifier = "identifiers-serverless"
  database_name      = "identifiers"
  master_username    = local.username
  master_password    = local.password

  db_security_group_id     = aws_security_group.database_sg.id
  aws_db_subnet_group_name = aws_db_subnet_group.default.name

  max_scaling_capacity = 16

  snapshot_identifier = "aurora-mysql-v3-pre-migration-24-10-08"

  engine_version = "8.0.mysql_aurora.3.08.2"
}

resource "aws_security_group" "database_v2_sg" {
  description = "Allows connection to identifiers-v2 RDS instance via TCP and egress to the world"
  vpc_id      = local.vpc_id_new
  name        = "identifiers_v2_database_sg"

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
  source = "./modules/rds-serverless"

  cluster_identifier = "identifiers-v2-serverless"
  database_name      = "identifiers"
  master_username    = local.username
  master_password    = null

  manage_master_user_password = true

  db_security_group_id     = aws_security_group.database_v2_sg.id
  aws_db_subnet_group_name = aws_db_subnet_group.default.name

  max_scaling_capacity = 16

  engine_version = "8.0.mysql_aurora.3.08.2"
}

resource "aws_security_group" "rds_v2_ingress_security_group" {
  name        = "pipeline_rds_v2_ingress_security_group"
  description = "Allow traffic to identifiers-v2 rds database"
  vpc_id      = local.vpc_id_new

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

