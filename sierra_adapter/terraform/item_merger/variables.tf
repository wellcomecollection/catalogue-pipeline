variable "updates_topic_arn" {}

variable "cluster_name" {}
variable "cluster_arn" {}
variable "vpc_id" {}

variable "container_image" {}

variable "dlq_alarm_arn" {}

variable "aws_region" {
  default = "eu-west-1"
}

variable "sierra_transformable_vhs_full_access_policy" {}
variable "sierra_transformable_vhs_dynamo_table_name" {}
variable "sierra_transformable_vhs_bucket_name" {}
variable "sierra_items_vhs_read_policy" {}
variable "sierra_items_vhs_dynamo_table_name" {}
variable "sierra_items_vhs_bucket_name" {}

variable "subnets" {
  type = list(string)
}

variable "namespace_id" {}
variable "namespace" {}
variable "interservice_security_group_id" {}
variable "service_egress_security_group_id" {}

variable "deployment_service_env" {}
variable "deployment_service_name" {}
