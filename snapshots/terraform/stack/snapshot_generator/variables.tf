variable "aws_region" {
  type = string
}
variable "snapshot_generator_input_topic_arn" {
  type = string
}
variable "dlq_alarm_arn" {
  type = string
}
variable "vpc_id" {
  type = string
}
variable "deployment_service_env" {
  type = string
}
variable "cluster_name" {
  type = string
}
variable "cluster_arn" {
  type = string
}
variable "snapshot_generator_image" {
  type = string
}
variable "subnets" {
  type = list(string)
}
variable "public_bucket_name" {
  type = string
}

variable "shared_logging_secrets" {
  type = map(string)
}

variable "es_bulk_size" {
  description = "How many works to fetch in a single scroll request"
  type        = number
}

variable "elastic_cloud_vpce_sg_id" {
  type = string
}
