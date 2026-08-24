variable "namespace" {
  type        = string
  description = "Namespace associated with the adapter (e.g. ebsco, axiell, folio)"
}

variable "s3_bucket_name" {
  type        = string
  description = "Name of S3 bucket associated with the adapter"
}


variable "repository_url" {
  type        = string
  description = "ECR repository URL for the Lambda function image"
}

variable "schedule_expression" {
  type        = string
  description = "Schedule pattern for adapter runs"
}

variable "schedule_enabled" {
  type        = bool
  default     = true
  description = "Whether the adapter run schedule is enabled. Set false to pause a misbehaving adapter without destroying the schedule resource."
}

variable "event_bus_name" {
  type        = string
  description = "Name of the EventBridge event bus associated with the adapter"
}

variable "steps_namespace" {
  type        = string
  description = "Namespace for the steps module path (e.g. ebsco, oai_pmh). Defaults to var.namespace."
  default     = null
}

variable "ecs_cluster_arn" {
  type        = string
  description = "ARN of the ECS cluster to run loader tasks in"
}

variable "subnets" {
  type        = list(string)
  description = "Subnet IDs for the ECS task network configuration"
}

variable "security_group_ids" {
  type        = list(string)
  description = "Security group IDs for the ECS task network configuration"
}

variable "task_repository_url" {
  type        = string
  description = "ECR repository URL for the ECS task image"
}

variable "enable_item_enrichment" {
  type        = bool
  default     = false
  description = "Run a FOLIO item-enrichment state between Run loader and Publish event. FOLIO-only; leave false for other adapters."
}

variable "enable_reconciliation" {
  type        = bool
  default     = false
  description = "Run a reconcile state between Run loader and Publish event, recording guid-change deletion facts before the completed event fires. Axiell-only; leave false for other adapters."
}

variable "task_token_timeout_seconds" {
  type    = number
  default = 21600
  # An ECS task that dies without calling SendTaskSuccess leaves the state
  # waiting forever: one axiell run sat at "Run loader" for 31 days. Normal runs
  # take about 70s, the longest observed legitimate run 2h50m, and a full Axiell
  # harvest about 4h, so 6h fails a stranded token the same day while leaving a
  # full harvest room.
  description = "How long a waitForTaskToken state waits for its ECS task before failing."
}
