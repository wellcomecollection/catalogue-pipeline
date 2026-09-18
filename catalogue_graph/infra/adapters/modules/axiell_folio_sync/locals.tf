locals {
  # "dev" only means anything when the dev target is enabled. Without it the
  # Lambda has no OKAPI_DEV_SECRET_PARAM, so a dev-defaulted run fails to find
  # its credentials, and because the state machine bakes this value in, that
  # takes the scheduled runs down too. Falling back to prod keeps the two
  # settings from combining into a broken deployment.
  folio_default_target = var.folio_dev_target_enabled ? var.folio_default_target : "prod"

  # Allow live writes only to the sandbox during validation. This prevents a
  # misconfiguration from enabling production writes.
  #
  # Remove this when production writes are wanted.
  dry_run_default = local.folio_default_target == "dev" ? var.dry_run_default : true
}
