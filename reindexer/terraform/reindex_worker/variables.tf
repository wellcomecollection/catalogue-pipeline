variable "aws_region" {
  default = "eu-west-1"
}

variable "account_id" {}

variable "reindex_worker_container_image" {}

variable "service_egress_security_group_id" {}

variable "cluster_name" {}
variable "cluster_arn" {}

variable "reindexer_jobs" {
  type = list(map(string))
}

variable "reindexer_job_config_json" {}

variable "service_name" {
  type = string
}
variable "service_env" {
  type = string
}

variable "vpc_id" {}
variable "private_subnets" {
  type = list(string)
}
variable "dlq_alarm_arn" {}
