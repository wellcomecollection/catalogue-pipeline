variable "name" {}
variable "namespace" {}

variable "cluster_arn" {}

variable "subnets" {
  type = list(string)
}

variable "namespace_id" {}

variable "security_group_ids" {
  type = list(string)
}

variable "aws_region" {}
