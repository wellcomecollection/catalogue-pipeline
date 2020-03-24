variable "input_queue_name" {}
variable "input_queue_url" {}
variable "output_topic_arn" {}

variable "cluster_arn" {}
variable "cluster_name" {}

variable "subnets" {
  type = list(string)
}

variable "namespace_id" {}

variable "security_group_ids" {
  type = list(string)
}

variable "aws_region" {}