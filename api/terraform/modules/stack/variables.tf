variable "namespace" {}
variable "environment" {}
variable "instance" {
  type    = string
  default = ""
}

variable "desired_task_count" {
  type = number
}

variable "subnets" {
  type = list(string)
}

variable "vpc_id" {}

variable "cluster_arn" {}

variable "listener_port" {}

variable "lb_arn" {}

variable "egress_security_group_id" {}

variable "lb_ingress_sg_id" {}

variable "interservice_sg_id" {}

variable "service_discovery_namespace_id" {}
