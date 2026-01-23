variable "name" {
  type = string
}

variable "service_name" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "shared_logging_secrets" {
  type = map(any)
}

variable "cluster_arn" {
  type = string
}

variable "namespace_id" {
  type    = string
  default = null
}

variable "subnets" {
  type = list(string)
}

variable "volumes" {
  type = list(object({
    name      = string
    host_path = string
  }))
  default = []
}

# This is intentionally untyped.
# If typed you can't have optional nulls which results in some complexity.
# See https://github.com/hashicorp/terraform/issues/19898
variable "container_definitions" {}

variable "desired_task_count" {
  type = number
}

variable "min_capacity" {
  type = number
}

variable "max_capacity" {
  type    = number
  default = 1
}

variable "scale_up_adjustment" {
  type    = number
  default = 1
}

variable "scale_down_adjustment" {
  type    = number
  default = -1
}

variable "cpu" {
  type    = number
  default = 512
}

variable "memory" {
  type    = number
  default = 1024
}

variable "use_fargate_spot" {
  type    = bool
  default = false
}

variable "launch_type" {
  type = string
}

variable "capacity_provider_strategies" {
  type = list(object({
    capacity_provider = string
    weight            = number
  }))
  default = []
}

variable "ordered_placement_strategies" {
  type = list(object({
    type  = string
    field = string
  }))
  default = []
}

variable "security_group_ids" {
  type    = list(string)
  default = []
}

variable "elastic_cloud_vpce_sg_id" {
  type = string
}

variable "queue_config" {
  type = object({
    name                       = string
    topic_arns                 = list(string)
    visibility_timeout_seconds = number
    message_retention_seconds  = number
    max_receive_count          = number
    cooldown_period            = string
    dlq_alarm_arn              = string

    main_q_age_alarm_action_arns = optional(list(string), [])
  })
}

variable "trigger_values" {
  type    = list(string)
  default = ["default"]
}

variable "ephemeral_storage_size" {
  type = number
  default = 21
}