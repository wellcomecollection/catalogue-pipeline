variable "name" {
  type = string
}
variable "namespace" {
  type = string
}

variable "subnets" {
  type = list(string)
}

variable "cluster_name" {
}

variable "cluster_arn" {
}

variable "security_group_ids" {
  type    = list(string)
  default = []
}

variable "egress_security_group_id" {
  type = string
}

variable "elastic_cloud_vpce_security_group_id" {
  type = string
}

variable "host_cpu" {
  type    = number
  default = 512
}

variable "host_memory" {
  type    = number
  default = 1024
}

variable "desired_task_count" {
  type    = number
  default = 1
}

variable "max_capacity" {
  type = number
}

variable "min_capacity" {
  type    = number
  default = 0
}

variable "scale_up_adjustment" {
  type    = number
  default = 1
}

variable "scale_down_adjustment" {
  type    = number
  default = -1
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

variable "volumes" {
  type = list(object({
    name      = string
    host_path = string
  }))
  default = []
}

variable "manager_mount_points" {
  type = list(object({
    containerPath = string
    sourceVolume  = string
  }))
  default = []
}

variable "apps" {
  type = map(object({
    image           = string,
    env_vars        = map(string),
    secret_env_vars = map(string),
    cpu             = number,
    memory          = number,
    healthcheck     = any,
    mount_points = list(object({
      containerPath = string,
      sourceVolume  = string
    }))
  }))
}

variable "manager_container_image" {}

variable "manager_container_name" {}

variable "manager_env_vars" {
  type = map(string)
}

variable "manager_secret_env_vars" {
  type    = map(string)
  default = {}
}

variable "manager_cpu" {
  type    = number
  default = 512
}

variable "manager_memory" {
  type    = number
  default = 1024
}

variable "deployment_service_env" {
  type = string
}

variable "shared_logging_secrets" {
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
