# OKAPI connection config for the Axiell → FOLIO sync Lambda.
resource "aws_ssm_parameter" "okapi_credentials" {
  name        = "/catalogue_pipeline/${var.namespace}/okapi_credentials"
  description = "OKAPI config (JSON: url, tenant, username, password) for the Axiell → FOLIO sync"
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
