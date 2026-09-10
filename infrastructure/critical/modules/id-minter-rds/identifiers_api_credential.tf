# The Identifiers API's own read-only database credential, granted to the Data
# API role in iam.tf (platform#6533). Terraform owns the password and the secret;
# create_identifiers_api_user.sh creates the MySQL user and makes its password
# match, because that is SQL and there is no AWS API for it.

resource "random_password" "identifiers_api_read" {
  count = local.identifiers_api_read_count

  length = 32

  # Excludes quotes, backslash, @, / and backtick, which would need escaping in
  # the statements the script issues.
  override_special = "!#$%^&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "identifiers_api_read" {
  count = local.identifiers_api_read_count

  name        = "rds/identifiers-v2-serverless${local.hyphen_suffix}/identifiers_api_read"
  description = "Read-only credential for the Identifiers API"

  # The credential is reproducible from this configuration, so a recovery window
  # protects nothing and would leave the name reserved against a later recreate.
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "identifiers_api_read" {
  count = local.identifiers_api_read_count

  secret_id = aws_secretsmanager_secret.identifiers_api_read[0].id

  secret_string = jsonencode({
    username = "identifiers_api_read"
    password = random_password.identifiers_api_read[0].result
  })
}
