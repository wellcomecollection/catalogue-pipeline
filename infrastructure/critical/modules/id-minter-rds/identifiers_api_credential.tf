# The Identifiers API's own read-only database credential, granted to the Data
# API role in iam.tf (platform#6533).
#
# create_identifiers_api_user.sh creates the MySQL user and writes the first
# value, so Terraform never holds the password and rotation owns it from the
# start. That is also why this is a bare secret rather than terraform-aws-secrets,
# which writes a value.

locals {
  # Kept in step with EXCLUDE_CHARACTERS in create_identifiers_api_user.sh.
  identifiers_api_password_exclude_characters = "\"'@/\\`"
}

resource "aws_secretsmanager_secret" "identifiers_api_read" {
  count = local.identifiers_api_read_count

  name        = "rds/identifiers-v2-serverless${local.hyphen_suffix}/identifiers_api_read"
  description = "Read-only credential for the Identifiers API"

  # The script regenerates these credentials from scratch, so a recovery window
  # protects nothing and would leave the name reserved against a later recreate.
  recovery_window_in_days = 0
}

# The rotation function connects to the database on 3306 rather than through the
# Data API, so unlike the API itself it has to sit in the VPC.
#
# It needs two security groups: this one for outbound access, and the cluster's
# ingress group for the database to accept it. That group declares no egress
# rules, which removes the default allow-all, so it grants no outbound access on
# its own. The id-minter Lambda carries the same pair.
resource "aws_security_group" "identifiers_api_rotation_egress" {
  count = local.identifiers_api_read_count

  name        = "identifiers_api_rotation_egress${local.underscore_suffix}"
  description = "Allow the Identifiers API rotation function egress"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "identifiers-api-rotation-egress${local.hyphen_suffix}"
  }
}

# AWS's published rotation function. Single user rather than alternating users,
# because that variant changes the password by connecting as the user itself and
# so needs no administrative credential.
resource "aws_serverlessapplicationrepository_cloudformation_stack" "identifiers_api_rotation" {
  count = local.identifiers_api_read_count

  name             = "identifiers-api-read-rotation${local.hyphen_suffix}"
  application_id   = "arn:aws:serverlessrepo:us-east-1:297356227824:applications/SecretsManagerRDSMySQLRotationSingleUser"
  semantic_version = "1.1.722"
  capabilities     = ["CAPABILITY_IAM", "CAPABILITY_RESOURCE_POLICY"]

  parameters = {
    functionName      = "identifiers-api-read-rotation${local.hyphen_suffix}"
    excludeCharacters = local.identifiers_api_password_exclude_characters
    vpcSubnetIds      = join(",", var.private_subnet_ids)

    # The function runs in the VPC, so it needs telling where Secrets Manager is.
    endpoint = "https://secretsmanager.eu-west-1.amazonaws.com"

    vpcSecurityGroupIds = join(",", [
      aws_security_group.identifiers_api_rotation_egress[0].id,
      aws_security_group.rds_v2_ingress_security_group.id,
    ])
  }
}

resource "aws_secretsmanager_secret_rotation" "identifiers_api_read" {
  count = local.identifiers_api_read_count

  secret_id           = aws_secretsmanager_secret.identifiers_api_read[0].id
  rotation_lambda_arn = aws_serverlessapplicationrepository_cloudformation_stack.identifiers_api_rotation[0].outputs["RotationLambdaARN"]

  # create_identifiers_api_user.sh writes the first value, so rotating on creation
  # would run against an empty secret. Trigger the first rotation by hand.
  rotate_immediately = false

  rotation_rules {
    automatically_after_days = 30
  }
}
