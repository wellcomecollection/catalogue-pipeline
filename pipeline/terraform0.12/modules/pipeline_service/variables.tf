variable "service_name" {}
variable "cluster_arn" {}
variable "cluster_name" {}

variable "subnets" {
  type = list(string)
}

variable "aws_region" {}
variable "namespace_id" {}
variable "container_image" {}

variable "secret_env_vars" {
  type = map(string)
}

variable "secret_env_vars_length" {}

variable "env_vars" {
  type = map(string)
}

variable "env_vars_length" {}

variable "security_group_ids" {
  default = []
  type = list(string)
}

variable "max_capacity" {}

variable "logstash_host" {}

variable "messages_bucket_arn" {}

variable "queue_read_policy" {}

variable "desired_task_count" {
  default = 1
}

variable "launch_type" {
  default = "FARGATE"
}

variable "cpu" {
  default = "256"
}

variable "memory" {
  default = "512"
}

variable "min_capacity" {
  default = 0
}