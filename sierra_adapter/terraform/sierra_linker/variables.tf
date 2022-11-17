variable "resource_type" {
  type = string
}

variable "container_image" {}

variable "demultiplexer_topic_arn" {}

variable "cluster_name" {}
variable "cluster_arn" {}

variable "dlq_alarm_arn" {}

variable "subnets" {
  type = list(string)
}

variable "namespace_id" {}
variable "namespace" {}
variable "interservice_security_group_id" {}
variable "service_egress_security_group_id" {}

variable "shared_logging_secrets" {
  type = map(any)
}

variable "elastic_cloud_vpce_sg_id" {
  type = string
}
