variable "pipeline_date" {
  type = string
}

variable "subnets" {
  type = list(string)
}

variable "vpc_id" {}

variable "aws_region" {}

variable "account_id" {}

variable "dlq_alarm_arn" {}

variable "rds_ids_access_security_group_id" {}

variable "release_label" {
  type = string
  validation {
    condition     = var.release_label == "stage" || var.release_label == "prod"
    error_message = "The release_label must be either stage or prod."
  }
}

variable "enable_reindexing" {
  type = bool
}

# Miro
variable "miro_adapter_topic_arns" {
  type = object({reindexer_topic=string, updates_topics=list(string)})
}
variable "vhs_miro_read_policy" {}
variable "enable_miro_reindexing" {
  type    = bool
  default = false
}

# Sierra
variable "vhs_sierra_read_policy" {}
variable "vhs_sierra_sourcedata_bucket_name" {}
variable "vhs_sierra_sourcedata_table_name" {}
variable "sierra_adapter_topic_arns" {
  type = object({reindexer_topic=string, updates_topics=list(string)})
}

variable "enable_sierra_reindexing" {
  type    = bool
  default = false
}

# Calm
variable "vhs_calm_read_policy" {}
variable "vhs_calm_sourcedata_bucket_name" {}
variable "vhs_calm_sourcedata_table_name" {}
variable "calm_adapter_topic_arns" {
  type = object({reindexer_topic=string, updates_topics=list(string)})
}

variable "enable_calm_reindexing" {
  type    = bool
  default = false
}

# Mets
variable "mets_adapter_read_policy" {}
variable "mets_adapter_table_name" {}
variable "mets_adapter_topic_arns" {
  type = object({reindexer_topic=string, updates_topics=list(string)})
}

variable "enable_mets_reindexing" {
  type    = bool
  default = false
}

variable "private_subnets" {
  type = list(string)
}

variable "read_storage_s3_role_arn" {}

variable "inferrer_model_data_bucket_name" {}
