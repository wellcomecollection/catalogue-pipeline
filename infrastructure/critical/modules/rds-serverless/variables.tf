variable "cluster_identifier" {
  type    = string
  default = null
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

variable "manage_master_user_password" {
  type    = bool
  default = null
}

variable "db_security_group_id" {
  type = string
}

variable "aws_db_subnet_group_name" {
  type = string
}

variable "snapshot_identifier" {
  type    = string
  default = null
}

variable "skip_final_snapshot" {
  description = "If true, allow destroy without creating a final snapshot. When false, destroy will fail unless the module is extended to set final_snapshot_identifier."
  type        = bool
  default     = false
}

variable "max_scaling_capacity" {
  type    = number
  default = 8.0
}

variable "min_scaling_capacity" {
  type    = number
  default = 0.5
}
