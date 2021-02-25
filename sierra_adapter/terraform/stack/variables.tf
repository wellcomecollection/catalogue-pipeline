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
