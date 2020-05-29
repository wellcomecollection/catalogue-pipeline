variable "namespace" {}
variable "vpc_id"{}
variable "release_label" {}
variable "lambda_error_alarm_arn" {}
variable "infra_bucket" {}
variable "dlq_alarm_arn" {}
variable "private_subnets" {}
variable "egress_security_group_id" {}
variable "interservice_security_group_id" {}
variable "bibs_windows_topic_arns" {}
variable "items_windows_topic_arns" {}
