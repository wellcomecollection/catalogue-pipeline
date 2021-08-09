variable "namespace" {
  type = string
}

variable "namespace_hyphen" {
  type = string
}

variable "merger_images_topic_arn" {
  type = string
}

variable "dlq_alarm_arn" {
  type = string
}

variable "aws_security_group_service_egress_id" {
  type = string
}

variable "ec_privatelink_security_group_id" {
  type = string
}

variable "aws_ecs_cluster_cluster_name" {
  type = string
}

variable "aws_ecs_cluster_cluster_arn" {
  type = string
}

variable "inference_capacity_provider_name" {
  type = string
}

variable "inference_manager_image" {
  type = string
}

variable "feature_inferrer_image" {
  type = string
}

variable "lsh_model_key" {
  type = string
}

variable "inferrer_model_data_bucket_name" {
  type = string
}

variable "palette_inferrer_image" {
  type = string
}

variable "aspect_ratio_inferrer_image" {
  type = string
}

variable "es_images_initial_index" {
  type = string
}

variable "es_images_augmented_index" {
  type = string
}

variable "pipeline_storage_es_service_secrets" {
  type = string
}

variable "subnets" {
  type = string
}

variable "min_capacity" {
  type = number
}

variable "max_capacity" {
  type = number
}

variable "scale_down_adjustment" {
  type = number
}

variable "scale_up_adjustment" {
  type = number
}

variable "release_label" {
  type = string
}

variable "shared_logging_secrets" {
  type = string
}

locals {
  namespace                            = var.namespace
  namespace_hyphen                     = var.namespace_hyphen
  merger_images_topic_arn              = var.merger_images_topic_arn
  dlq_alarm_arn                        = var.dlq_alarm_arn
  aws_security_group_service_egress_id = var.aws_security_group_service_egress_id
  ec_privatelink_security_group_id     = var.ec_privatelink_security_group_id
  aws_ecs_cluster_cluster_name         = var.aws_ecs_cluster_cluster_name
  aws_ecs_cluster_cluster_arn          = var.aws_ecs_cluster_cluster_arn
  inference_capacity_provider_name     = var.inference_capacity_provider_name
  inference_manager_image              = var.inference_manager_image
  feature_inferrer_image               = var.feature_inferrer_image
  lsh_model_key                        = var.lsh_model_key
  inferrer_model_data_bucket_name      = var.inferrer_model_data_bucket_name
  palette_inferrer_image               = var.palette_inferrer_image
  aspect_ratio_inferrer_image          = var.aspect_ratio_inferrer_image
  es_images_initial_index              = var.es_images_initial_index
  es_images_augmented_index            = var.es_images_augmented_index
  pipeline_storage_es_service_secrets  = var.pipeline_storage_es_service_secrets
  subnets                              = var.subnets
  min_capacity                         = var.min_capacity
  max_capacity                         = var.max_capacity
  scale_down_adjustment                = var.scale_down_adjustment
  scale_up_adjustment                  = var.scale_up_adjustment
  release_label                        = var.release_label
  shared_logging_secrets               = var.shared_logging_secrets
}
