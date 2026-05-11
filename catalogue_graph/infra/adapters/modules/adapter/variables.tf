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
