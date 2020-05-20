variable "namespace" {}
variable "environment" {}

variable "desired_task_count" {
  type = number
}

variable "subnets" {
  type = list(string)
}

variable "vpc_id" {}

variable "cluster_arn" {}

variable "listener_port" {}

variable "api_id" {}

variable "lb_arn" {}

variable "egress_security_group_id" {}

variable "lb_ingress_sg_id" {}

variable "logstash_host" {}

variable "interservice_sg_id" {}

variable "domain_name" {}
variable "certificate_arn" {}

variable "api_gateway_id" {}

variable "service_discovery_namespace_id" {}
