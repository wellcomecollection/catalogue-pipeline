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
variable "cluster_name" {}
variable "cluster_arn" {}

variable "container_image" {}

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
