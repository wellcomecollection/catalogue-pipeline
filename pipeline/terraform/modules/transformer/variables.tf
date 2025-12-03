variable "source_name" {
  type = string
}

variable "adapter_config" {
  type = object({
    topics        = list(string)
    reindex_topic = string
    read_policy   = string
  })
}

variable "listen_to_reindexer" {
  type = bool
}

variable "container_image" {
  type = string
}

variable "queue_visibility_timeout_seconds" {
  type = number
}

variable "env_vars" {
  type = map(string)
}

variable "secret_env_vars" {
  type    = map(string)
  default = {}
}

variable "cpu" {
  type = number
}

variable "memory" {
  type = number
}

variable "min_capacity" {
  type = number
}

variable "max_capacity" {
  type = number
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

variable "trigger_values" {
  type = list(string)
  default = [ "default" ]
}