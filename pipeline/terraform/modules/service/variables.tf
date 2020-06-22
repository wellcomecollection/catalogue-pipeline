variable "service_name" {}
variable "cluster_arn" {}
variable "cluster_name" {}

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

variable "messages_bucket_arn" {}

variable "queue_read_policy" {}

variable "max_capacity" {
  type = number
}

variable "desired_task_count" {
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

variable "min_capacity" {
  type    = number
  default = 0
}
