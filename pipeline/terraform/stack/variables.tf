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
  type = map
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

# Miro
variable "miro_adapter_topic_arns" {
  type = list(string)
}
variable "vhs_miro_read_policy" {}
variable "vhs_miro_table_name" {}

# Sierra
variable "vhs_sierra_read_policy" {}
variable "vhs_sierra_sourcedata_bucket_name" {}
variable "vhs_sierra_sourcedata_table_name" {}
variable "sierra_adapter_topic_arns" {
  type = list(string)
}

# Calm
variable "vhs_calm_read_policy" {}
variable "vhs_calm_sourcedata_bucket_name" {}
variable "vhs_calm_sourcedata_table_name" {}
variable "calm_adapter_topic_arns" {
  type = list(string)
}

# Mets
variable "mets_adapter_read_policy" {}
variable "mets_adapter_table_name" {}
variable "mets_adapter_topic_arns" {
  type = list(string)
}

variable "private_subnets" {
  type = list(string)
}

variable "storage_bucket_name" {
  type = string
}

variable "inferrer_model_data_bucket_name" {}
