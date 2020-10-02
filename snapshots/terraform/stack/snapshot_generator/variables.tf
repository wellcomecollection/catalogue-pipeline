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
