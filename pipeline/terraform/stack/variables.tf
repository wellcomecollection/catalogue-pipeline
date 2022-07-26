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

variable "reindexing_state" {
  type = object({
    listen_to_reindexer      = bool
    scale_up_tasks           = bool
    scale_up_elastic_cluster = bool
    scale_up_id_minter_db    = bool
    scale_up_matcher_db      = bool
  })
}

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

variable "adapter_config" {
  type = map(object({
    topics        = list(string)
    reindex_topic = string
  }))
}

variable "rds_config" {
  type = object({
    cluster_id        = string
    subnet_group      = string
    security_group_id = string
  })
}

variable "logging_cluster_id" {
  type = string
}
