variable "namespace" {
  description = "Namespace applied to all resource names (matches the Lambda function name)"
  type        = string
  default     = "axiell-folio-sync"
}

variable "repository_url" {
  description = "URL of the shared unified_pipeline_lambda ECR image (the sync step ships inside it)"
  type        = string
}

variable "image_tag" {
  description = "Tag of the shared image to deploy (dev for scripts/deploy_lambda.sh, prod for CI)"
  type        = string
  default     = "dev"
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
  default     = 512
}

variable "event_bus_name" {
  description = "Name of the EventBridge event bus publishing axiell.adapter.completed events"
  type        = string
  default     = "catalogue-pipeline-adapter-event-bus"
}

variable "manifest_bucket_name" {
  description = "S3 bucket name for NDJSON manifest storage. If empty, auto-generated."
  type        = string
  default     = ""
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

variable "max_sync_retries" {
  description = "Maximum number of retry attempts for Lambda invocation in the Step Function"
  type        = number
  default     = 3
}
