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

# The same, for the EBSCO FOLIO test environment the test environment holds production data, 
# so only its own parameter must ever point a run at it. It is public SaaS like prod, 
# so it needs no VPC and is not gated on a flag.
resource "aws_ssm_parameter" "okapi_credentials_test" {
  name        = "/catalogue_pipeline/${var.namespace}/okapi_credentials_test"
  description = "OKAPI config (JSON: url, tenant, username, password) for the FOLIO test environment"
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
