variable "name" {
  description = "Name prefix for all resources"
  type        = string
}

variable "sns_topic_arn" {
  description = "ARN of the SNS topic to subscribe to"
  type        = string
}

variable "state_machine_arn" {
  description = "ARN of the Step Functions state machine to target"
  type        = string
}

variable "queue_visibility_timeout_seconds" {
  description = "Visibility timeout for the SQS queue"
  type        = number
  default     = 30
}

variable "queue_message_retention_seconds" {
  description = "Message retention period (default 4 days)"
  type        = number
  default     = 345600
}

variable "queue_max_receive_count" {
  description = "Max receives before DLQ"
  type        = number
  default     = 4
}

variable "dlq_alarm_arn" {
  description = "ARN of SNS topic for DLQ alarms"
  type        = string
  default     = null
}

variable "batch_size" {
  description = "Max records per batch"
  type        = number
  default     = 10
}

variable "maximum_batching_window_in_seconds" {
  description = "Max time to gather records before invoking target"
  type        = number
  default     = 0
}

variable "enabled" {
  description = "Whether the pipe is enabled"
  type        = bool
  default     = true
}
