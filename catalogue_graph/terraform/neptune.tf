resource "aws_neptune_cluster" "catalogue_graph_cluster" {
  cluster_identifier                   = "catalogue-graph"
  engine                               = "neptune"
  engine_version                       = "1.4.2.0"
  neptune_cluster_parameter_group_name = "default.neptune1.4"
  iam_database_authentication_enabled  = true
  apply_immediately                    = true
  storage_encrypted                    = true
  vpc_security_group_ids = [
    aws_security_group.neptune_security_group.id,
    aws_security_group.neptune_service_security_group.id
  ]
  neptune_subnet_group_name = aws_db_subnet_group.neptune_subnet_group.name
  iam_roles                 = [aws_iam_role.catalogue_graph_cluster.arn]

  # Set minimum capacity to 1 NCU, and maximum capacity to 16 NCUs. These are the minimum possible values.
  serverless_v2_scaling_configuration {
    min_capacity = 1
    max_capacity = 32
  }
}

resource "aws_iam_role" "catalogue_graph_cluster" {
  name = "catalogue-graph-cluster"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "rds.amazonaws.com" # Neptune uses RDS for some operations
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

# Read-only access to the bulk load S3 bucket
data "aws_iam_policy_document" "neptune_s3_read_only_policy" {
  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]
    resources = [
      aws_s3_bucket.neptune_bulk_upload_bucket.arn,
      "${aws_s3_bucket.neptune_bulk_upload_bucket.arn}/*"
    ]
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

# Allow any egress traffic. The Neptune cluster needs to be able to reach the `wellcomecollection-neptune-graph-loader`
# S3 bucket for bulk loading.
resource "aws_vpc_security_group_egress_rule" "neptune_egress" {
  security_group_id = aws_security_group.neptune_security_group.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_secretsmanager_secret" "neptune_cluster_endpoint" {
  name = "${local.namespace}/neptune-cluster-endpoint"
}

resource "aws_secretsmanager_secret_version" "neptune_cluster_endpoint_value" {
  secret_id     = aws_secretsmanager_secret.neptune_cluster_endpoint.id
  secret_string = aws_neptune_cluster.catalogue_graph_cluster.endpoint
}
