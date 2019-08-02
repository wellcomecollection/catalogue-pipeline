variable "name" {}

variable "infra_bucket" {
  default = "wellcomecollection-platform-infra"
}

variable "bucket_name_prefix" {
  default = "wellcomecollection-vhs-"
}

variable "table_name_prefix" {
  default = "vhs-"
}

variable "aws_region" {
  default = "eu-west-1"
}

variable "read_principals" {
  default = []
  type    = "list"
}
