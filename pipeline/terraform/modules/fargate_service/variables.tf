variable "name" {
  type = string
}

variable "container_image" {
  type = string
}

variable "secret_env_vars" {
  type    = map(string)
  default = {}
}

variable "env_vars" {
  type = map(string)
}

variable "omit_queue_url" {
  type    = bool
  default = false
}

variable "topic_arns" {
  type = list(string)
}

variable "queue_name" {
  type    = string
  default = null
}

variable "entrypoint" {
  type    = list(string)
  default = null
}

variable "command" {
  default = null
  type    = list(string)
}

variable "queue_visibility_timeout_seconds" {
  type    = number
  default = 30
}

variable "message_retention_seconds" {
  type = number
  # The actual default on SQS is four whole days.
  # This is sufficient to cope with normal bank holiday weekends.
  default = 345600
}

variable "max_receive_count" {
  type    = number
  default = 4
}

variable "security_group_ids" {
  default = []
  type    = list(string)
}

variable "cpu" {
  type    = number
  default = 512
}

variable "memory" {
  type    = number
  default = 1024
}

variable "min_capacity" {
  type    = number
  default = 0
}

variable "max_capacity" {
  type = number
}

variable "desired_task_count" {
  type    = number
  default = 1
}

variable "use_fargate_spot" {
  type    = bool
  default = true
}

variable "cooldown_period" {
  type    = string
  default = "1m"
}

# This is intentionally untyped.
# If typed you can't have optional nulls which results in some complexity.
# See https://github.com/hashicorp/terraform/issues/19898
variable "fargate_service_boilerplate" {}

variable "service_discovery_namespace_id" {
  type    = string
  default = null
}
