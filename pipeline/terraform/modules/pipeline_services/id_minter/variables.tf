variable "pipeline_date" {
  type = string
}

variable "namespace" {
  type    = string
  default = ""
}

variable "include_id_generator" {
  type    = bool
  default = true
}

variable "vpc_config" {
  type = object({
    subnet_ids         = list(string)
    security_group_ids = list(string)
  })
}

variable "env_vars" {
  type = object({
    RDS_MAX_CONNECTIONS         = number
    LOG_LEVEL                   = optional(string, "INFO")
    ES_SOURCE_INDEX_PREFIX      = optional(string, "works-source")
    ES_TARGET_INDEX_PREFIX      = optional(string, "works-identified")
    ES_SOURCE_INDEX_DATE_SUFFIX = optional(string)
    ES_TARGET_INDEX_DATE_SUFFIX = optional(string)
    APPLY_MIGRATIONS            = optional(string, "false")
    S3_BUCKET                   = optional(string)
    S3_PREFIX                   = optional(string, "dev")
  })
}

variable "secret_env_vars" {
  type = map(string)
}

# Name of the Secrets Manager secret holding the RDS credentials. The Lambdas
# read it on every connection so that they pick up the automatic 7 day rotation
# of the managed master user password. Read access comes from the secret refs
# in secret_env_vars, which cover the same secret.
variable "rds_secret_name" {
  type = string
}

variable "alarm_topic_arn" {
  type = string
}