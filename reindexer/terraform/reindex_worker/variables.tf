variable "aws_region" {
  default = "eu-west-1"
}

variable "account_id" {}

variable "reindex_worker_container_image" {}

variable "service_egress_security_group_id" {}

variable "cluster_name" {}
variable "cluster_arn" {}

variable "namespace_id" {}

variable "reindexer_jobs" {
  type = list(string)
}

variable "reindexer_job_config_json" {}

variable "scale_up_period_in_minutes" {
  default = 1
}

variable "scale_down_period_in_minutes" {
  default = 10
}

variable "namespace" {}

variable "vpc_id" {}
variable "private_subnets" {
  type = list(string)
}
variable "dlq_alarm_arn" {}
