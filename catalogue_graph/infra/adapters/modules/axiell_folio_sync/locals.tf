locals {
  # "dev" only means anything when the dev target is enabled. Without it the
  # Lambda has no OKAPI_DEV_SECRET_PARAM, so a dev-defaulted run fails to find
  # its credentials, and because the state machine bakes this value in, that
  # takes the scheduled runs down too. Falling back to prod keeps the two
  # settings from combining into a broken deployment.
  folio_default_target = var.folio_dev_target_enabled ? var.folio_default_target : "prod"
}
