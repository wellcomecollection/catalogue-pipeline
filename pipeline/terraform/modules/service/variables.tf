variable "service_name" {}
variable "cluster_arn" {}
variable "cluster_name" {}
variable "shared_logging_secrets" {
  type = map(any)
}
variable "subnets" {
  type = list(string)
}

variable "container_image" {}

variable "secret_env_vars" {
  type = map(string)
}

variable "env_vars" {
  type = map(string)
}

variable "security_group_ids" {
  default = []
  type    = list(string)
}

variable "elastic_cloud_vpce_sg_id" {
  type = string
}

variable "queue_read_policy" {}

variable "cpu" {
  type    = number
  default = 512
}

variable "memory" {
  type    = number
  default = 1024
}

variable "desired_task_count" {
  type    = number
  default = 1
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
  type   = number
  default = -1
}

variable "launch_type" {
  type    = string
  default = "FARGATE"
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

variable "deployment_service_env" {
  type = string
}
variable "deployment_service_name" {
  type = string
}

variable "use_fargate_spot" {
  type    = bool
  default = false
}
