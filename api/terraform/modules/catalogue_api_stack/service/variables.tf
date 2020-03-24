variable "subnets" {
  type = list(string)
}

variable "cluster_arn" {}

variable "namespace" {}
variable "namespace_id" {}

variable "vpc_id" {}

variable "container_image" {}
variable "container_port" {}

variable "nginx_container_image" {}
variable "nginx_container_port" {}

variable "security_group_ids" {
  type = list(string)
}

variable "lb_arn" {}
variable "listener_port" {}

variable "desired_task_count" {
  type = number
}

variable "logstash_host" {}
