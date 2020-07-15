variable "name" {
  type = string
}

variable "image" {
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

variable "env_vars" {
  type    = map(string)
  default = {}
}

variable "secret_env_vars" {
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

variable "app_cpu" {
  type    = number
  default = 512
}

variable "app_memory" {
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

variable "deployment_service_name" {
  type    = string
  default = ""
}
variable "deployment_service_env" {
  type    = string
  default = "prod"
}
