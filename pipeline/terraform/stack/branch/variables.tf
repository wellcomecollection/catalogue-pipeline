variable "is_reindexing" {
  type        = bool
  description = "Are you reindexing through this pipeline right now?"
}

variable "namespace_hyphen" {
  type = string
}

variable "batcher_image" {
  type = string
}

variable "relation_embedder_image" {
  type = string
}

variable "router_image" {
  type = string
}

variable "merger_image" {
  type = string
}

variable "inference_manager_image" {
  type = string
}

variable "feature_inferrer_image" {
  type = string
}

variable "ingestor_works_image" {
  type = string
}

variable "palette_inferrer_image" {
  type = string
}

variable "aspect_ratio_inferrer_image" {
  type = string
}

variable "ingestor_images_image" {
  type = string
}

variable "ec_privatelink_security_group_id" {
  type = string
}

variable "service_egress_security_group_id" {
  type = string
}
variable "cluster_name" {
  type = string
}

variable "shared_logging_secrets" {
  type = map(any)
}

variable "min_capacity" {
  type = number
}

variable "max_capacity" {
  type = number
}

variable "subnets" {
  type = list(string)
}
variable "scale_down_adjustment" {
  type = number
}
variable "scale_up_adjustment" {
  type = number
}
variable "dlq_alarm_arn" {}
variable "release_label" {
  type = string
}
variable "elasticsearch_users" {
}
variable "es_works_merged_index" {
  type = string
}
variable "es_works_identified_index" {
  type = string
}
variable "es_images_initial_index" {
  type = string
}
variable "es_images_augmented_index" {
  type = string
}
variable "es_images_index" {
  type = string
}
variable "es_works_denormalised_index" {
  type = string
}
variable "es_works_index" {
  type = string
}
variable "pipeline_storage_es_service_secrets" {
}
variable "pipeline_storage_private_host" {
  type = string
}
variable "pipeline_storage_protocol" {
  type = string
}
variable "pipeline_storage_port" {
  type = string
}

variable "pipeline_date" {
  type = string
}

variable "matcher_topic_arn" {
  type = string
}

variable "inference_capacity_provider_name" {
  type = string
}

variable "inferrer_model_data_bucket_name" {}


variable "toggle_tei_on" {
}

variable "inferrer_lsh_model_key_value" {
}
