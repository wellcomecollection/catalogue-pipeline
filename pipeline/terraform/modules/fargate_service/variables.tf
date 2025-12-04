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

variable "fargate_service_boilerplate" {
  type = object({
    egress_security_group_id             = optional(string, null)
    elastic_cloud_vpce_security_group_id = optional(string, null)

    cluster_name = optional(string, null)
    cluster_arn  = optional(string, null)

    scale_down_adjustment = optional(number, null)
    scale_up_adjustment   = optional(number, null)

    dlq_alarm_topic_arn          = optional(string, null)
    main_q_age_alarm_action_arns = optional(list(string), null)

    subnets   = optional(list(string), null)
    namespace = optional(string, null)

    shared_logging_secrets = optional(map(any), null)
  })
}

variable "service_discovery_namespace_id" {
  type    = string
  default = null
}

variable "trigger_values" {
  type    = list(string)
  default = ["default"]
}