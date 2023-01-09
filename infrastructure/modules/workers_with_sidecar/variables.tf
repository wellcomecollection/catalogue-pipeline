variable "name" {
  type = string
}

variable "service_name" {
  type    = string
  default = ""
}

variable "cluster_name" {
  type = string
}

variable "cluster_arn" {
  type = string
}

variable "shared_logging_secrets" {
  type = map(any)
}

variable "namespace_id" {
  type    = string
  default = null
}

variable "subnets" {
  type = list(string)
}

variable "desired_task_count" {
  type    = number
  default = 1
}

variable "min_capacity" {
  type    = number
  default = 1
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

variable "security_group_ids" {
  type    = list(string)
  default = []
}

variable "elastic_cloud_vpce_sg_id" {
  type = string
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

variable "sidecar_image" {
  type = string
}

variable "sidecar_name" {
  type = string
}

variable "sidecar_env_vars" {
  type    = map(string)
  default = {}
}

variable "sidecar_secret_env_vars" {
  type    = map(string)
  default = {}
}

variable "sidecar_cpu" {
  type    = number
  default = 512
}

variable "sidecar_memory" {
  type    = number
  default = 1024
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

variable "volumes" {
  type = list(object({
    name      = string
    host_path = string
  }))
  default = []
}

variable "sidecar_mount_points" {
  type = list(object({
    containerPath = string
    sourceVolume  = string
  }))
  default = []
}
