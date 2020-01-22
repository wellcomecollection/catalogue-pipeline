variable "merged_dynamo_table_name" {}

variable "reindexed_items_topic_arn" {}
variable "updates_topic_arn" {}

variable "cluster_name" {}
variable "vpc_id" {}

variable "container_image" {}

variable "dlq_alarm_arn" {}

variable "aws_region" {
  default = "eu-west-1"
}

variable "vhs_full_access_policy" {}
variable "bucket_name" {}

variable "subnets" {
  type = "list"
}

variable "namespace_id" {}
variable "interservice_security_group_id" {}
variable "service_egress_security_group_id" {}

variable "sierra_items_bucket" {}
