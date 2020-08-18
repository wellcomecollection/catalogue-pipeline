variable "name" {
  type = string
}

variable "cluster_name" {
  type = string
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

variable "app_env_vars" {
  type    = map(string)
  default = {}
}

variable "app_secret_env_vars" {
  type    = map(string)
  default = {}
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

variable "app_image" {
  type = string
}

variable "app_name" {
  type = string
}

variable "app_cpu" {
  type    = number
  default = 512
}

variable "app_memory" {
  type    = number
  default = 1024
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

variable "app_healthcheck" {
  type    = any
  default = null
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

variable "app_mount_points" {
  type = list(object({
    containerPath = string
    sourceVolume  = string
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

variable "deployment_service_name" {
  type        = string
  description = "Used by weco-deploy to determine which services to deploy, if unset the value used will be var.name"
  default     = ""
}

variable "deployment_service_env" {
  type        = string
  description = "Used by weco-deploy to determine which services to deploy in conjunction with deployment_service_name"
  default     = "prod"
}
