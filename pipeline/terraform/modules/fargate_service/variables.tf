variable "name" {
  type = string
}
variable "namespace" {
  type = string
}

variable "cluster_arn" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "shared_logging_secrets" {
  type = map(string)
}

variable "subnets" {
  type = list(string)
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

variable "dlq_alarm_topic_arn" {
  type = string
}

variable "security_group_ids" {
  default = []
  type    = list(string)
}

variable "egress_security_group_id" {
  type = string
}

variable "elastic_cloud_vpce_security_group_id" {
  type = string
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

variable "scale_up_adjustment" {
  type    = number
  default = 1
}

variable "scale_down_adjustment" {
  type    = number
  default = -1
}

variable "deployment_service_env" {
  type = string
}

variable "use_fargate_spot" {
  type    = bool
  default = true
}
