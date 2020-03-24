variable "namespace" {}
variable "environment" {}

variable "task_desired_count" {}

variable "subnets" {
  type = "list"
}

variable "vpc_id" {}

variable "api_container_image" {}
variable "nginx_container_image" {}

variable "cluster_name" {}

variable "listener_port" {}

variable "api_id" {}

variable "lb_arn" {}

variable "lb_ingress_sg_id" {}

variable "logstash_host" {}

variable "interservice_sg_id" {}
