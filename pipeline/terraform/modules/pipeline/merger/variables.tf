variable "pipeline_date" {
  type = string
}

variable "index_date" {
  type    = string
  default = null
}

variable "vpc_config" {
  type = object({
    subnet_ids         = list(string)
    security_group_ids = list(string)
  })
}

variable "es_config" {
  type = object({
    es_host     = string
    es_port     = string
    es_protocol = string
    es_apikey   = string
  })
}

variable "es_index_config" {
  type = object({
    es_works_identified_index   = optional(string, null)
    es_works_denormalised_index = optional(string, null)
    es_images_initial_index     = optional(string, null)
  })
  default = {}
}

variable "queue_config" {
  type = object({
    name       = optional(string, null)
    topic_arns = optional(list(string), [])
    // Note this must be greater than or equal to the lambda timeout
    visibility_timeout_seconds = optional(number, 30)
    // 4 days, to allow message retention if something goes wrong over a weekend
    message_retention_seconds = optional(number, 345600)
    max_receive_count         = optional(number, 4)
    dlq_alarm_arn             = optional(string, null)

    # Batching configuration
    batch_size              = optional(number, 1)
    batching_window_seconds = optional(number, null)

    # Scaling configuration
    maximum_concurrency = optional(number, 2)
  })
  default = null
}