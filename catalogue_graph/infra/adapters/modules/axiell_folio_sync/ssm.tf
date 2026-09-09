# OKAPI connection config for the Axiell to Folio sync Lambda.
resource "aws_ssm_parameter" "okapi_credentials" {
  name        = "/catalogue_pipeline/${var.namespace}/okapi_credentials"
  description = "OKAPI config (JSON: url, tenant, username, password) for the Axiell to Folio sync"
  type        = "SecureString"
  value = jsonencode({
    url      = "placeholder"
    tenant   = "placeholder"
    username = "placeholder"
    password = "placeholder"
  })

  lifecycle {
    ignore_changes = [value]
  }
}

# The same, for the FOLIO dev sandbox. Kept as a separate parameter so pointing a
# run at the sandbox (folio_target="dev") cannot disturb the prod connection
# config — and so the prod parameter never holds a sandbox url by accident.
#
data "aws_secretsmanager_secret_version" "folio_dev_password" {
  count = var.folio_dev_target_enabled ? 1 : 0

  secret_id = var.folio_dev_okapi.password_secret_id
}

resource "aws_ssm_parameter" "okapi_credentials_dev" {
  count = var.folio_dev_target_enabled ? 1 : 0

  name        = "/catalogue_pipeline/${var.namespace}/okapi_credentials_dev"
  description = "OKAPI config (JSON: url, tenant, username, password) for the FOLIO dev sandbox"
  type        = "SecureString"
  value = jsonencode({
    url      = var.folio_dev_okapi.url
    tenant   = var.folio_dev_okapi.tenant
    username = var.folio_dev_okapi.username
    password = data.aws_secretsmanager_secret_version.folio_dev_password[0].secret_string
  })
}
