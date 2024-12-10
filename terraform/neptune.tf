resource "aws_neptune_cluster" "catalogue_graph_cluster" {
  cluster_identifier                   = "catalogue-graph"
  engine                               = "neptune"
  engine_version                       = "1.3.2.1"
  neptune_cluster_parameter_group_name = "default.neptune1.3"
  iam_database_authentication_enabled  = true
  apply_immediately                    = true
  storage_encrypted                    = true
  vpc_security_group_ids               = [aws_security_group.neptune_security_group.id]
  neptune_subnet_group_name            = aws_db_subnet_group.neptune_subnet_group.name

  # Set minimum capacity to 1 NCU, and maximum capacity to 16 NCUs. These are the minimum possible values.
  serverless_v2_scaling_configuration {
    min_capacity = 1
    max_capacity = 16
  }
}

resource "aws_neptune_cluster_instance" "catalogue_graph_instance" {
  cluster_identifier           = aws_neptune_cluster.catalogue_graph_cluster.cluster_identifier
  instance_class               = "db.serverless"
  neptune_parameter_group_name = "default.neptune1.3"
}

resource "aws_db_subnet_group" "neptune_subnet_group" {
  name       = "catalogue-graph"
  subnet_ids = local.private_subnets
}

resource "aws_security_group" "neptune_security_group" {
  name   = "catalogue-graph-neptune"
  vpc_id = data.aws_vpc.vpc.id
}

# Only allow ingress traffic from the developer VPC
resource "aws_vpc_security_group_ingress_rule" "neptune_ingress" {
  security_group_id = aws_security_group.neptune_security_group.id
  cidr_ipv4         = data.aws_vpc.vpc.cidr_block
  ip_protocol       = "-1"
}

resource "aws_secretsmanager_secret" "neptune_cluster_endpoint" {
  name = "NeptuneTest/InstanceEndpoint"
}

resource "aws_secretsmanager_secret_version" "neptune_cluster_endpoint_value" {
  secret_id     = aws_secretsmanager_secret.neptune_cluster_endpoint.id
  secret_string = aws_neptune_cluster.catalogue_graph_cluster.endpoint
}
