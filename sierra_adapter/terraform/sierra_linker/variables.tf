variable "resource_type" {
  type = string
}

variable "container_image" {}

variable "demultiplexer_topic_arn" {}

variable "cluster_name" {}
variable "cluster_arn" {}
variable "vpc_id" {}

variable "dlq_alarm_arn" {}

variable "aws_region" {
  default = "eu-west-1"
}

variable "subnets" {
  type = list(string)
}

variable "namespace_id" {}
variable "namespace" {}
variable "interservice_security_group_id" {}
variable "service_egress_security_group_id" {}

variable "deployment_service_env" {}
variable "deployment_service_name" {}
variable "shared_logging_secrets" {
  type = map(any)
}

variable "elastic_cloud_vpce_sg_id" {
  type = string
}
