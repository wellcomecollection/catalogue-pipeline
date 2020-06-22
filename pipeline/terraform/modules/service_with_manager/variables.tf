variable "subnets" {
  type = list(string)
}

variable "cluster_name" {
}

variable "cluster_arn" {
}

variable "service_name" {
}

variable "desired_task_count" {
  type    = number
  default = 1
}

variable "security_group_ids" {
  type    = list(string)
  default = []
}

variable "host_cpu" {
  type    = number
  default = 512
}

variable "host_memory" {
  type    = number
  default = 1024
}

variable "max_capacity" {
  type = number
}

variable "min_capacity" {
  type    = number
  default = 0
}

variable "messages_bucket_arn" {}

variable "queue_read_policy" {}

variable "app_container_image" {}

variable "app_container_name" {
  type = string
}

variable "app_env_vars" {
  type = map(string)
}

variable "app_secret_env_vars" {
  type    = map(string)
  default = {}
}

variable "app_cpu" {
  type    = number
  default = 512
}

variable "app_memory" {
  type    = number
  default = 1024
}

variable "app_healthcheck" {
  type    = any
  default = null
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

