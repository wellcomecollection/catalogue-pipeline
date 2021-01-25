variable "pipeline_date" {
  type = string
}

variable "max_capacity" {
  type        = number
  default     = 10
  description = "The max capacity of every ECS service will be less than or equal to this value"
}

variable "subnets" {
  type = list(string)
}
variable "shared_logging_secrets" {
  type = map(any)
}

variable "vpc_id" {}

variable "aws_region" {}

variable "dlq_alarm_arn" {}

variable "rds_cluster_id" {
  type = string
}
variable "rds_subnet_group_name" {
  type = string
}
variable "extra_rds_instances" {
  type        = number
  default     = 0
  description = "How many *extra* RDS instances to add to enable greater ID minter throughput"
}

variable "rds_ids_access_security_group_id" {}
variable "pipeline_storage_security_group_id" {
  type = string
}

variable "release_label" {
  type = string
  validation {
    condition     = var.release_label == "stage" || var.release_label == "prod"
    error_message = "The release_label must be either stage or prod."
  }
}

# Miro
variable "miro_adapter_topic_arns" {
  type = list(string)
}
variable "vhs_miro_read_policy" {}

# Sierra
variable "vhs_sierra_read_policy" {}
variable "sierra_adapter_topic_arns" {
  type = list(string)
}

# Calm
variable "vhs_calm_read_policy" {}
variable "calm_adapter_topic_arns" {
  type = list(string)
}

# Mets
variable "mets_adapter_topic_arns" {
  type = list(string)
}

variable "storage_bucket_name" {
  type = string
}

variable "inferrer_model_data_bucket_name" {}

variable "pipeline_storage_id" {
  default     = "pipeline_storage"
  description = "The ID of the pipeline_storage instance used for secrets"
}

locals {
  pipeline_storage_es_host     = "elasticsearch/${var.pipeline_storage_id}/private_host"
  pipeline_storage_es_port     = "catalogue/${var.pipeline_storage_id}/es_port"
  pipeline_storage_es_protocol = "catalogue/${var.pipeline_storage_id}/es_protocol"

  pipeline_storage_service_list = [
    "id_minter",
    "matcher",
    "merger",
    "transformer",
    "relation_embedder",
    "router",
    "inferrer",
  ]

  pipeline_storage_es_service_secrets = zipmap(local.pipeline_storage_service_list, [
    for service in local.pipeline_storage_service_list :
    {
      es_host     = local.pipeline_storage_es_host
      es_port     = local.pipeline_storage_es_port
      es_protocol = local.pipeline_storage_es_protocol
      es_username = "catalogue/${var.pipeline_storage_id}/${service}/es_username"
      es_password = "catalogue/${var.pipeline_storage_id}/${service}/es_password"
    }
  ])
}
