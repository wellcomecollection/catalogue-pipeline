variable "cluster_identifier" {}

variable "database_name" {}

variable "username" {}

variable "password" {}

variable "vpc_security_group_ids" {
  type = list(string)
}

variable "db_security_group_id" {
  type = string
}

variable "instance_class" {}

variable "aws_db_subnet_group_name" {
  type = string
}
