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
  type = string
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

variable "app_port" {
  // Leaving this as the default, -1, will not expose a port on the container
  type    = number
  default = -1
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
  type    = string
  default = ""
}
