locals {
  namespace = "catalogue-${var.pipeline_date}_${var.service_name}"

  # Preserve the existing production graph table name for the primary matcher
  # service, but give non-prod matcher instances their own distinct namespace 
  base_namespace = var.service_name == "matcher" ? "catalogue-${var.pipeline_date}" : local.namespace

  graph_table_billing_mode = var.scale_up_matcher_db ? "PROVISIONED" : "PAY_PER_REQUEST"
  lock_table_billing_mode  = var.scale_up_matcher_db ? "PROVISIONED" : "PAY_PER_REQUEST"

  lock_timeout = 1 * 60

  # The records in the locktable expire after local.lock_timeout
  # The matcher is able to override locks that have expired
  # Wait slightly longer to make sure locks are expired
  queue_visibility_timeout_seconds = local.lock_timeout + 30

  # Epistemic status of this comment: somewhat speculative.
  #
  # We want a lot of redrives here so we can handle cases where a bunch
  # of works are being merged together in quick succession.
  #
  # Consider: ten works A, B, C, …, J are all going to be merged together,
  # and they arrive at the matcher in that order.
  #
  # The matcher starts processing work A, and acquires a lock on it.
  # The nine other works try to get a lock on A, fail, get redriven.
  #
  # When the visibility timeout expires, the matcher starts processing B
  # and acquires a lock on A.  The eight other works try to get a lock on A,
  # fail, get redriven.
  max_receive_count = 10
}