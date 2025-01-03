variable "namespace" {
  type = string
}

variable "pipeline_date" {
  type = string
}

variable "reindexing_state" {
  type = object({
    listen_to_reindexer      = bool
    scale_up_tasks           = bool
    scale_up_elastic_cluster = bool
    scale_up_id_minter_db    = bool
    scale_up_matcher_db      = bool
  })
}

variable "es_works_merged_index" {
  type = string
}

variable "es_works_denormalised_index" {
  type = string
}

variable "pipeline_storage_es_service_secrets" {
  type = map(object({
    es_host     = string
    es_port     = string
    es_protocol = string
    es_apikey   = string
  }))
}

variable "path_concatenator_image" {
  type = string
}

variable "router_image" {
  type = string
}

variable "router_input_topic_arn" {
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

variable "fargate_service_boilerplate" {}


