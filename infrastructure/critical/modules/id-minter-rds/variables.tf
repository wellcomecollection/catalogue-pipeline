variable "vpc_id" {}

variable "private_subnet_ids" {
  type = list(string)
}

variable "admin_cidr_ingress" {}

variable "master_username" {}

variable "engine_version" {}

variable "max_scaling_capacity" {
  default = 16
}

variable "name_suffix" {
  description = "Optional suffix appended to resource names so multiple instances of the module can co-exist. Leave empty to preserve the original (un-suffixed) names."
  type        = string
  default     = ""
}

variable "snapshot_identifier" {
  description = "Optional identifier (or ARN) of an RDS cluster snapshot to restore from on initial creation."
  type        = string
  default     = null
}
