# OKAPI credentials for the Axiell → FOLIO sync Lambda.
resource "aws_ssm_parameter" "okapi_credentials" {
  name        = "/catalogue_pipeline/${var.namespace}/okapi_credentials"
  description = "OKAPI service-account credentials (JSON) for the Axiell → FOLIO sync"
  type        = "SecureString"
  value       = jsonencode({ username = "placeholder", password = "placeholder" })

  lifecycle {
    ignore_changes = [value]
  }
}
