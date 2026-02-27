# -------------------------------------------------------
# ECS migration task — bulk-loads a parquet S3 export into
# the identifiers-v2 RDS cluster (RFC 083 two-table schema).
#
# Run manually with:
#
#   aws ecs run-task \
#     --cluster id-minter-migration \
#     --task-definition id-minter-migration \
#     --launch-type FARGATE \
#     --network-configuration '{
#       "awsvpcConfiguration": {
#         "subnets": ["<private-subnet-id>"],
#         "securityGroups": ["<migration-sg-id>", "<rds-v2-ingress-sg-id>"],
#         "assignPublicIp": "DISABLED"
#       }
#     }' \
#     --overrides '{
#       "containerOverrides": [{
#         "name": "id-minter-migration",
#         "command": [
#           "/app/src/id_minter/steps/migration.py",
#           "--use-ecs",
#           "--event", "{\"export_date\":\"2026-02-26\",\"truncate\":true}"
#         ]
#       }]
#     }' \
#     --profile platform-developer
# -------------------------------------------------------

# -------------------------------------------------------
# Data sources
# -------------------------------------------------------

data "aws_ecr_repository" "unified_pipeline_task" {
  name = "uk.ac.wellcome/unified_pipeline_task"
}

locals {
  # Extract the secret name from the full ARN.
  # ARN format: arn:aws:secretsmanager:region:account:secret:NAME-RANDOM
  # We strip the 6-char random suffix so the name works with the
  # ECS secrets injection (secretsmanager:GetSecretValue).
  migration_rds_v2_master_secret_name = regex(
    "arn:aws:secretsmanager:[^:]+:[^:]+:secret:(.+)-.{6}$",
    module.identifiers_v2_serverless_rds_cluster.master_user_secret_arn
  )[0]
}

# -------------------------------------------------------
# ECS cluster + task definition
# -------------------------------------------------------

resource "aws_ecs_cluster" "migration" {
  name = "id-minter-migration"
}

module "migration_ecs_task" {
  source = "../../pipeline/terraform/modules/ecs_task"

  task_name = "id-minter-migration"
  image     = "${data.aws_ecr_repository.unified_pipeline_task.repository_url}:dev"

  environment = {
    IDENTIFIERS_DATABASE = "identifiers"
    APPLY_MIGRATIONS     = "true"
  }

  secret_env_vars = {
    RDS_PRIMARY_HOST = "rds/identifiers-v2-serverless/endpoint"
    RDS_REPLICA_HOST = "rds/identifiers-v2-serverless/reader_endpoint"
    RDS_PORT         = "rds/identifiers-v2-serverless/port"
    RDS_USERNAME     = "${local.migration_rds_v2_master_secret_name}:username"
    RDS_PASSWORD     = "${local.migration_rds_v2_master_secret_name}:password"
  }

  cpu    = 4096
  memory = 16384
}

# -------------------------------------------------------
# IAM — S3 read access for the exports bucket
# -------------------------------------------------------

data "aws_iam_policy_document" "migration_s3_read" {
  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket",
    ]
    resources = [
      aws_s3_bucket.id_minter.arn,
      "${aws_s3_bucket.id_minter.arn}/*",
    ]
  }
}

resource "aws_iam_role_policy" "migration_s3_read" {
  role   = module.migration_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.migration_s3_read.json
}

# -------------------------------------------------------
# IAM — CloudWatch Logs
# -------------------------------------------------------

data "aws_iam_policy_document" "migration_cloudwatch_write" {
  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "migration_cloudwatch_write" {
  role   = module.migration_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.migration_cloudwatch_write.json
}

# -------------------------------------------------------
# Networking — security group for the migration task
# -------------------------------------------------------

resource "aws_security_group" "migration_task" {
  name        = "id-minter-migration-task"
  description = "Security group for the id-minter migration ECS task"
  vpc_id      = local.vpc_id_new

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "id-minter-migration-task"
  }
}

# Allow the migration task to reach the v2 RDS cluster
resource "aws_security_group_rule" "migration_to_rds_v2" {
  type                     = "ingress"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.migration_task.id
  security_group_id        = aws_security_group.database_v2_sg.id
}
