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

variable "manager_mount_points" {
  type = list(object({
    containerPath = string
    sourceVolume  = string
  }))
  default = []
}

variable "messages_bucket_arn" {}

variable "queue_read_policy" {}

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

variable "deployment_service_name" {
  type = string
}
