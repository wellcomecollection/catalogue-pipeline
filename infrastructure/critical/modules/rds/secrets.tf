resource "aws_secretsmanager_secret" "endpoint" {
  name = "rds/${var.cluster_identifier}/endpoint"
}

resource "aws_secretsmanager_secret_version" "endpoint" {
  secret_id     = aws_secretsmanager_secret.endpoint.id
  secret_string = aws_rds_cluster.default.endpoint
}

resource "aws_secretsmanager_secret" "reader_endpoint" {
  name = "rds/${var.cluster_identifier}/reader_endpoint"
}

resource "aws_secretsmanager_secret_version" "reader_endpoint" {
  secret_id     = aws_secretsmanager_secret.reader_endpoint.id
  secret_string = aws_rds_cluster.default.reader_endpoint
}

resource "aws_secretsmanager_secret" "port" {
  name = "rds/${var.cluster_identifier}/port"
}

resource "aws_secretsmanager_secret_version" "port" {
  secret_id     = aws_secretsmanager_secret.port.id
  secret_string = aws_rds_cluster.default.port
}
