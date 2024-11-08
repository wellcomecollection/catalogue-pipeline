variable "tag_override" {
  type    = string
  default = ""
}

variable "ecr_repository_name" {
  type = string
}

variable "service_name" {
  type = string
}

variable "description" {
  type    = string
  default = ""
}

variable "pipeline_date" {
  type = string
}


variable "environment_variables" {
  type        = map(string)
  description = "Arbitrary environment variables to give to the Lambda"
  default     = {}
}

variable "timeout" {
  default     = 30
  description = "lambda function timeout"
}

variable "memory_size" {
  default     = 1024
  description = "lambda function memory size"
}

variable "queue_config" {
  type = object({
    name       = optional(string, null)
    topic_arns = optional(list(string), [])
    // Note this must be greater than or equal to the lambda timeout
    visibility_timeout_seconds = optional(number, 30)
    // 4 days, to allow message retention if something goes wrong over a weekend
    message_retention_seconds = optional(number, 345600)
    max_receive_count         = optional(number, 4)
    dlq_alarm_arn             = optional(string, null)

    # Batching configuration
    batch_size              = optional(number, 1)
    batching_window_seconds = optional(number, null)

    # Scaling configuration
    maximum_concurrency = optional(number, 2)
  })
}

variable "event_source_enabled" {
  type    = bool
  default = true
}

