variable "source_name" {
  type = string
}

variable "adapter_config" {
  type = object({
    topics        = list(string)
    reindex_topic = string
    read_policy   = string
  })
}

variable "listen_to_reindexer" {
  type = bool
}

variable "container_image" {
  type = string
}

variable "queue_visibility_timeout_seconds" {
  type = number
}

variable "env_vars" {
  type = map(string)
}

variable "secret_env_vars" {
  type    = map(string)
  default = {}
}

variable "cpu" {
  type = number
}

variable "memory" {
  type = number
}

variable "min_capacity" {
  type    = number
}

variable "max_capacity" {
  type = number
}

variable "fargate_service_boilerplate" {
  type = object({
    egress_security_group_id             = string
    elastic_cloud_vpce_security_group_id = string

    cluster_name = string
    cluster_arn  = string

    scale_down_adjustment = number
    scale_up_adjustment   = number

    dlq_alarm_topic_arn = string

    subnets = list(string)

    namespace = string

    deployment_service_env = string

    shared_logging_secrets = map(any)
  })
}
