variable "min_capacity" {
  type = number
}

variable "max_capacity" {
  type = number
}

variable "vpc_id" {}
variable "cluster_arn" {}
variable "cluster_name" {}

variable "service_name" {}

variable "source_queue_arn" {}
variable "source_queue_name" {}

variable "subnets" {
  type = list(string)
}

variable "desired_task_count" {
  type = number
}

variable "memory" {
  description = "How much memory to allocate to the app"
  type        = number
}

variable "cpu" {
  description = "How much CPU to allocate to the app"
  type        = number
}

variable "aws_region" {
  description = "AWS Region the task will run in"
}

variable "container_image" {
  description = "Container image to run"
}

variable "env_vars" {
  description = "Environment variables to pass to the container"
  type        = map(string)
  default     = {}
}

variable "security_group_ids" {
  type = list(string)
}

variable "namespace_id" {}
