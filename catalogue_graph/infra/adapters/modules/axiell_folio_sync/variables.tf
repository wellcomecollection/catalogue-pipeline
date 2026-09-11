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
  # Loading the RefCache alone peaks near 870 MB against prod, so anything below
  # about 1 GB is OOM-killed before a record is processed.
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

# FOLIO dev server target. Enabling it attaches the Lambda to the VPC and adds a
# second OKAPI SecureString, read only by folio_target="dev" runs.
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

# Sets FOLIO_TARGET, and the target the state machine falls back to. "dev"
# points the scheduled runs at the sandbox and requires folio_dev_target_enabled.
variable "folio_default_target" {
  description = "FOLIO instance to use when an event does not specify one: \"prod\" (EBSCO SaaS) or \"dev\" (the sandbox, which requires folio_dev_target_enabled)."
  type        = string
  default     = "prod"

  validation {
    condition     = contains(["prod", "dev"], var.folio_default_target)
    error_message = "folio_default_target must be \"prod\" or \"dev\"."
  }
}

variable "max_sync_retries" {
  description = "Maximum number of retry attempts for Lambda invocation in the Step Function"
  type        = number
  default     = 3
}
