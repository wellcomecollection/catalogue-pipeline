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

# The same, for the FOLIO dev sandbox. It is a separate parameter so that
# pointing a run at the sandbox (folio_target="dev") cannot disturb the prod
# connection config, and so the prod parameter never holds a sandbox url by
# accident.
#

resource "aws_ssm_parameter" "okapi_credentials_dev" {
  count = var.folio_dev_target_enabled ? 1 : 0

  name        = "/catalogue_pipeline/${var.namespace}/okapi_credentials_dev"
  description = "OKAPI config (JSON: url, tenant, username, password) for the FOLIO dev sandbox"
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
