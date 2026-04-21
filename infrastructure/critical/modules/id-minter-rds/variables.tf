variable "vpc_id" {}

variable "private_subnet_ids" {
  type = list(string)
}

variable "admin_cidr_ingress" {}

variable "master_username" {}

variable "engine_version" {
  default = "8.0.mysql_aurora.3.10.3"
}

variable "max_scaling_capacity" {
  default = 16
}
