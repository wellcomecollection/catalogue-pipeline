variable "aws_region" {
  type = string
}

variable "account_id" {}

variable "reindex_worker_container_image" {}

variable "service_egress_security_group_id" {}

variable "elastic_cloud_vpce_sg_id" {
  type = string
}

variable "cluster_name" {}
variable "cluster_arn" {}

variable "reindexer_jobs" {
  type = list(map(string))
}

variable "reindexer_job_config_json" {}

variable "service_name" {
  type = string
}

variable "private_subnets" {
  type = list(string)
}
variable "dlq_alarm_arn" {}
variable "shared_logging_secrets" {
  type = map(any)
}
