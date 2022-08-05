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

variable "topic_arns" {
  type = list(string)
}

variable "queue_visibility_timeout_seconds" {
  type    = number
  default = 30
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
    egress_security_group_id             = string
    elastic_cloud_vpce_security_group_id = string

    cluster_name = string
    cluster_arn  = string

    scale_down_adjustment = number
    scale_up_adjustment   = number

    dlq_alarm_topic_arn = string

    subnets = list(string)

    namespace = string

    deployment_service_env = string

    shared_logging_secrets = map(any)
  })
}
