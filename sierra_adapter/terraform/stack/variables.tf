variable "namespace" {}
variable "vpc_id" {}
variable "release_label" {}
variable "lambda_error_alarm_arn" {}
variable "infra_bucket" {}
variable "dlq_alarm_arn" {}
variable "private_subnets" {}
variable "egress_security_group_id" {}
variable "interservice_security_group_id" {}
variable "bibs_windows_topic_arns" {}
variable "items_windows_topic_arns" {}
variable "holdings_windows_topic_arns" {}

variable "orders_windows_topic_arns" {
  type = list(string)
}

variable "deployment_env" {}
variable "shared_logging_secrets" {
  type = map(any)
}

variable "sierra_reader_image" {
  type = string
}

variable "sierra_linker_image" {
  type = string
}

variable "sierra_merger_image" {
  type = string
}

variable "sierra_indexer_image" {
  type = string
}

variable "reporting_reindex_topic_arn" {
  type = string
}

variable "elastic_cloud_vpce_sg_id" {
  type = string
}
