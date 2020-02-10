variable "aws_region" {
  default = "eu-west-1"
}

variable "infra_bucket" {}

variable "namespace" {
  default = "data_api"
}

variable "snapshot_generator_release_uri" {}

variable "critical_slack_webhook" {}

variable "vpc_id" {}

variable "private_subnets" {
  type = "list"
}

variable "data_page_url" {
  default = "data.wellcomecollection.org"
}

variable "route_zone_id" {}
