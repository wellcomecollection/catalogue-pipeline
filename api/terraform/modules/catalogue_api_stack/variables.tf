variable "namespace" {}
variable "environment" {}

variable "task_desired_count" {}

variable "subnets" {
  type = "list"
}

variable "vpc_id" {}

variable "cluster_name" {}

variable "listener_port" {}

variable "api_id" {}

variable "lb_arn" {}

variable "lb_ingress_sg_id" {}

variable "logstash_host" {}

variable "interservice_sg_id" {}

variable "domain_name" {}
variable "certificate_arn" {}
variable "route53_zone_id" {}

variable "api_gateway_id" {}
