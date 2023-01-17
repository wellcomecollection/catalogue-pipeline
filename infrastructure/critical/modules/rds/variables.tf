variable "cluster_identifier" {}

variable "database_name" {}

variable "username" {}

variable "password" {}

variable "engine" {
  type = string
}

variable "db_security_group_id" {
  type = string
}

variable "instance_class" {}

variable "aws_db_subnet_group_name" {
  type = string
}

variable "instance_count" {
  type    = number
  default = 2
}

variable "db_parameter_group_name" {
  default = null
}
