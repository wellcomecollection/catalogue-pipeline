variable "topic_arns" {
  type = list(string)
}

variable "sierra_adapter_bucket" {
  type = string
}

variable "container_image" {}

variable "namespace_id" {}
variable "namespace" {}
variable "interservice_security_group_id" {}

variable "fargate_service_boilerplate" {}
