variable "namespace" {
  description = "Namespace applied to all resource names (matches the Lambda function name)"
  type        = string
  default     = "axiell-folio-sync"
}

variable "repository_url" {
  description = "URL of the shared unified_pipeline_lambda ECR image (the sync step ships inside it)"
  type        = string
}

variable "s3_table_bucket_arn" {
  description = "ARN of the Axiell adapter S3 Tables bucket (bucket scope, not table ARN); used to scope IAM read access"
  type        = string
}

variable "lambda_timeout_seconds" {
  description = "Lambda timeout in seconds; 300 comfortably handles 200-record windows"
  type        = number
  default     = 300
}

variable "lambda_memory_mb" {
  description = "Lambda memory in MB"
  type        = number
  # 512 OOM-killed the function: loading the RefCache (seven reference
  # collections at limit=2000) exhausts it before any record is processed.
  # Measured peaks for a *single-record* run at 2048 on 2026-09-09 were 722 MB
  # against the dev tenant and 867 MB against prod (prod carries more reference
  # data), so 1024 would leave only ~15% headroom before per-record cost is
  # added. 2048 also cuts the run from ~6.3s to ~1.9s, as Lambda scales CPU
  # with memory.
  default = 2048
}

variable "event_bus_name" {
  description = "Name of the EventBridge event bus publishing axiell.adapter.completed events"
  type        = string
  default     = "catalogue-pipeline-adapter-event-bus"
}

variable "manifest_bucket_name" {
  description = "S3 bucket name for JSON run report storage."
  type        = string
}

variable "manifest_retention_days" {
  description = "Days to retain manifests in S3 before expiration"
  type        = number
  default     = 90
}

variable "dry_run_default" {
  description = "Default dry_run value for the Lambda. Set to false once validated against FOLIO."
  type        = bool
  default     = true
}

# ── FOLIO dev server target (opt-in; prod is unaffected when disabled) ───────
#
# The dev server has no public endpoint, so reaching it needs the Lambda's ENIs
# in its VPC. Enabling this creates a second OKAPI SecureString and sets
# OKAPI_DEV_SECRET_PARAM, which the step reads only for folio_target="dev" runs.
# See docs/axiell-folio-sync-lambda-dev-instance.md.
variable "folio_dev_target_enabled" {
  description = "Attach the Lambda to the FOLIO dev server's subnets and give it a dev OKAPI parameter. Runs still default to prod; a run opts in with folio_target=\"dev\"."
  type        = bool
  default     = false
}

variable "folio_dev_subnets" {
  description = "Private subnets for the sync Lambda's ENIs when folio_dev_target_enabled is true (the catalogue VPC private subnets, where the FOLIO dev server runs)"
  type        = list(string)
  default     = []
}

variable "folio_dev_security_group_ids" {
  description = "Security groups for the sync Lambda's ENIs when folio_dev_target_enabled is true; must be VPC-scoped to the catalogue VPC"
  type        = list(string)
  default     = []
}

# Sets the FOLIO_TARGET env var, which resolve_folio_target uses when an event
# does not name a target itself. An event that *does* name one still wins, so
# this changes the default rather than forcing anything.
#
# Setting this to "dev" redirects every run that does not say otherwise —
# including the scheduled EventBridge → Step Functions pipeline — at the sandbox.
# It requires folio_dev_target_enabled; without it OKAPI_DEV_SECRET_PARAM is
# unset and runs fail rather than silently falling back to production.
variable "folio_default_target" {
  description = "FOLIO instance to use when an event does not specify one: \"prod\" (EBSCO SaaS) or \"dev\" (the sandbox, which requires folio_dev_target_enabled)."
  type        = string
  default     = "prod"

  validation {
    condition     = contains(["prod", "dev"], var.folio_default_target)
    error_message = "folio_default_target must be \"prod\" or \"dev\"."
  }
}

# Composed by the caller from live data rather than written out by hand, so a
# rebuild of the sandbox (which changes its private IP) does not leave a stale
# url behind. ``password_secret_id`` names a Secrets Manager entry; the password
# itself is read at plan time and never appears in this configuration.
variable "folio_dev_okapi" {
  description = "OKAPI connection for the FOLIO dev server. Null when folio_dev_target_enabled is false."
  type = object({
    url                = string
    tenant             = string
    username           = string
    password_secret_id = string
  })
  default = null
}

variable "max_sync_retries" {
  description = "Maximum number of retry attempts for Lambda invocation in the Step Function"
  type        = number
  default     = 3
}
