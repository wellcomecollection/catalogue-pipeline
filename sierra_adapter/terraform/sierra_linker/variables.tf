variable "resource_type" {
  type = string
}

variable "container_image" {}

variable "demultiplexer_topic_arn" {}

variable "namespace_id" {}
variable "namespace" {}
variable "interservice_security_group_id" {}

variable "fargate_service_boilerplate" {}
