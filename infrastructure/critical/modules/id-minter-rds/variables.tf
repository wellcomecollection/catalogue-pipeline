variable "vpc_id" {
  description = "ID of the VPC where the RDS resources will be created."
  type        = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "admin_cidr_ingress" {
  description = "CIDR block allowed administrative ingress access."
  type        = string
}

variable "master_username" {
  description = "Master username for the RDS cluster."
  type        = string
}

variable "engine_version" {
  description = "Engine version to use for the RDS cluster."
  type        = string
}

variable "max_scaling_capacity" {
  description = "Maximum Aurora Serverless scaling capacity."
  type        = number
  default     = 16
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
