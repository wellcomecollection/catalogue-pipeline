variable "container_image" {}

variable "demultiplexer_topic_arn" {}

variable "cluster_name" {}
variable "vpc_id" {}

variable "dlq_alarm_arn" {}

variable "aws_region" {
  default = "eu-west-1"
}

variable "subnets" {
  type = "list"
}

variable "vhs_sierra_items_full_access_policy" {}
variable "vhs_sierra_items_table_name" {}
variable "vhs_sierra_items_bucket_name" {}

variable "namespace_id" {}
variable "interservice_security_group_id" {}
variable "service_egress_security_group_id" {}
