resource "aws_neptune_cluster" "catalogue_graph_cluster" {
  cluster_identifier                   = "catalogue-graph"
  engine                               = "neptune"
  engine_version                       = "1.4.5.1"
  neptune_cluster_parameter_group_name = "default.neptune1.4"
  iam_database_authentication_enabled  = true
  apply_immediately                    = true
  storage_encrypted                    = true
  vpc_security_group_ids = [
    aws_security_group.neptune_security_group.id
  ]
  neptune_subnet_group_name = aws_db_subnet_group.neptune_subnet_group.name
  iam_roles                 = [aws_iam_role.catalogue_graph_cluster.arn]

  # Set minimum capacity to 1 NCU, and maximum capacity to 32 NCUs.
  serverless_v2_scaling_configuration {
    min_capacity = 1
    max_capacity = 32
  }
}

resource "aws_iam_role_policy" "s3_read_only_policy_attachment" {
  role   = aws_iam_role.catalogue_graph_cluster.name
  policy = data.aws_iam_policy_document.neptune_s3_read_only_policy.json
}

resource "aws_neptune_cluster_instance" "catalogue_graph_instance" {
  cluster_identifier           = aws_neptune_cluster.catalogue_graph_cluster.cluster_identifier
  instance_class               = "db.serverless"
  neptune_parameter_group_name = "default.neptune1.4"
}

resource "aws_db_subnet_group" "neptune_subnet_group" {
  name       = "catalogue-graph"
  subnet_ids = var.private_subnets
}
