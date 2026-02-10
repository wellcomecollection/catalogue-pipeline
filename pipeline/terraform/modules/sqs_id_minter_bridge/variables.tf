variable "name" {
  description = "Name prefix for all resources"
  type        = string
}

variable "sns_topic_arns" {
  description = "List of SNS topic ARNs to subscribe to"
  type        = list(string)
}

variable "lambda_arn" {
  description = "ARN of the id_minter_lambda_step_function Lambda"
  type        = string
}

variable "dlq_alarm_arn" {
  description = "ARN of SNS topic for DLQ alarms"
  type        = string
  default     = null
}

variable "batch_size" {
  description = "Max records per batch from SQS"
  type        = number
  default     = 10
}

variable "maximum_batching_window_in_seconds" {
  description = "Max time to gather records before invoking target"
  type        = number
  default     = 0
}

variable "queue_visibility_timeout_seconds" {
  description = "Visibility timeout for SQS queues"
  type        = number
  default     = 300 # 5 minutes, matches Lambda timeout
}

variable "enabled" {
  description = "Whether the pipes are enabled"
  type        = bool
  default     = true
}
