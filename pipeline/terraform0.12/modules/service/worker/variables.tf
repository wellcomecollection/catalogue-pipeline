variable "env_vars" {
  type = map(string)
}

variable "secret_env_vars" {
  type    = map(string)
  default = {}
}

variable "subnets" {
  type = list(string)
}

variable "container_image" {
}

variable "namespace_id" {
}

variable "cluster_name" {
}

variable "cluster_arn" {
}

variable "service_name" {
}

variable "desired_task_count" {
  type = number
  default = 1
}

variable "launch_type" {
  default = "FARGATE"
}

variable "security_group_ids" {
  type    = list(string)
  default = []
}

variable "cpu" {
  type = number
  default = 512
}

variable "memory" {
  type = number
  default = 1024
}
