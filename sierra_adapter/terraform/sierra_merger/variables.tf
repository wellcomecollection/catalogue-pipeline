variable "resource_type" {
  type = string
}

variable "vhs_read_write_policy" {
  type = string
}

variable "vhs_table_name" {
  type = string
}

variable "vhs_bucket_name" {
  type = string
}

variable "updates_topic_arn" {}

variable "container_image" {}

variable "namespace_id" {}
variable "namespace" {}
variable "interservice_security_group_id" {}

variable "fargate_service_boilerplate" {}
