locals {
  # "dev" only means anything when the dev target is enabled. Without it the
  # Lambda has no OKAPI_DEV_SECRET_PARAM, so a dev-defaulted run fails to find
  # its credentials, and because the state machine bakes this value in, that
  # takes the scheduled runs down too. Falling back to the test environment
  # keeps the two settings from combining into a broken deployment, without
  # sending the scheduled runs to production.
  folio_default_target = (
    var.folio_default_target == "dev" && !var.folio_dev_target_enabled
    ? "test"
    : var.folio_default_target
  )

  # Allow live writes only to the test environment and the sandbox during
  # validation. This prevents a misconfiguration from enabling production
  # writes.
  #
  # Remove this when production writes are wanted.
  dry_run_default = contains(["test", "dev"], local.folio_default_target) ? var.dry_run_default : true
}
