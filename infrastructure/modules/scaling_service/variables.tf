variable "name" {
  type = string
}

variable "service_name" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "cluster_arn" {
  type = string
}

variable "subnets" {
  type = list(string)
}

variable "namespace_id" {
  type    = string
}

variable "desired_task_count" {
  type = number
}

variable "use_fargate_spot" {
  type    = bool
  default = false
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

variable "shared_logging_secrets" {
  type = map(any)
}

variable "container_definitions" {
  type = list(string)
}

variable "cpu" {
  type    = number
  default = 512
}

variable "memory" {
  type    = number
  default = 1024
}

variable "launch_type" {
  type = string
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
