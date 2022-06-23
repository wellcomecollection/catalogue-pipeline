variable "pipeline_date" {
  type = string
}

variable "min_capacity" {
  type    = number
  default = 0
}

variable "max_capacity" {
  type        = number
  default     = 12
  description = "The max capacity of every ECS service will be less than or equal to this value"
}

variable "subnets" {
  type = list(string)
}
variable "shared_logging_secrets" {
  type = map(any)
}

variable "vpc_id" {}

variable "dlq_alarm_arn" {}

variable "rds_cluster_id" {
  type = string
}
variable "rds_subnet_group_name" {
  type = string
}

variable "reindexing_state" {
  type = object({
    connect_reindex_topics   = bool
    scale_up_tasks           = bool
    scale_up_elastic_cluster = bool
    scale_up_id_minter_db    = bool
    scale_up_matcher_db      = bool
  })
}

variable "rds_ids_access_security_group_id" {}
variable "ec_privatelink_security_group_id" {
  type = string
}

variable "tei_adapter_bucket_name" {
}
variable "release_label" {
  type = string
}

# Miro
variable "vhs_miro_read_policy" {}

# Sierra
variable "vhs_sierra_read_policy" {}

# Calm
variable "vhs_calm_read_policy" {}

variable "storage_bucket_name" {
  type = string
}

variable "inferrer_model_data_bucket_name" {}

variable "traffic_filter_platform_vpce_id" {
  type = string
}

variable "traffic_filter_catalogue_vpce_id" {
  type = string
}

variable "traffic_filter_public_internet_id" {
  type = string
}

variable "adapters" {
  type = map(object({
    topics        = list(string)
    reindex_topic = string
  }))
}

variable "logging_cluster_id" {
  type = string
}
