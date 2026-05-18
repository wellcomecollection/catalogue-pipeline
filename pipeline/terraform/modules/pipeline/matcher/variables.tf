variable "pipeline_date" {
  type = string
}

variable "service_name" {
  type    = string
  default = "matcher"
}

variable "vpc_config" {
  type = object({
    subnet_ids         = list(string)
    security_group_ids = list(string)
  })
}

variable "es_works_identified_index" {
  type        = string
  description = "Name of the works-identified ES index to read from"
}

variable "secret_env_vars" {
  type    = map(string)
  default = {}
}

variable "ecr_repository_name" {
  type    = string
  default = "uk.ac.wellcome/matcher"
}

variable "queue_config" {
  type = object({
    name                       = optional(string, null)
    topic_arns                 = optional(list(string), [])
    visibility_timeout_seconds = optional(number, 30)
    message_retention_seconds  = optional(number, 345600)
    max_receive_count          = optional(number, 4)
    dlq_alarm_arn              = optional(string, null)
    batch_size                 = optional(number, 1)
    batching_window_seconds    = optional(number, null)
    maximum_concurrency        = optional(number, 2)
  })
  default = null
}

variable "timeout" {
  type    = number
  default = 30
}

variable "memory_size" {
  type    = number
  default = 1024
}

variable "scale_up_matcher_db" {
  type        = bool
  default     = false
  description = "Whether to use provisioned capacity for DynamoDB tables"
}

variable "lock_timeout" {
  type    = number
  default = 60
}
