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

variable "release_label" {
  type = string
}

variable "inferrer_model_data_bucket_name" {}

# Fields:
#
#   - `topics` -- that the adapter will write to in normal operation
#   - `reindex_topic` -- that the reindexer will write to
#   - `read_policy` -- an IAM policy document that will allow a service
#     to read from the adapter store
#
variable "adapter_config" {
  type = map(object({
    topics        = list(string)
    reindex_topic = string
    read_policy   = string
  }))
}

variable "logging_config" {
  type = object({
    shared_secrets     = map(any)
    logging_cluster_id = string
  })
}

variable "network_config" {
  type = object({
    vpc_id                           = string
    subnets                          = list(string)
    ec_privatelink_security_group_id = string
    traffic_filters                  = list(string)
  })
}

variable "rds_config" {
  type = object({
    cluster_id        = string
    subnet_group      = string
    security_group_id = string
  })
}
