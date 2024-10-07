variable "cluster_identifier" {
  type = string
}

variable "database_name" {
  type = string
}

variable "master_username" {
  type = string
}

variable "master_password" {
  type = string
}

variable "db_security_group_id" {
  type = string
}

variable "aws_db_subnet_group_name" {
  type = string
}

variable "snapshot_identifier" {
  type = string
}


variable "max_scaling_capacity" {
  type    = number
  default = 8.0
}

variable "min_scaling_capacity" {
  type    = number
  default = 0.5
}
