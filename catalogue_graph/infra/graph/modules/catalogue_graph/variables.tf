variable "namespace" {
  type        = string
  description = "Namespace associated with the Neptune cluster."
}

variable "vpc_id" {
  type        = string
  description = "ID of the VPC which should contain the Neptune cluster."
}

variable "public_subnets" {
  type        = list(string)
  description = "List of public subnets associated with the VPC."
}

variable "private_subnets" {
  type        = list(string)
  description = "List of private subnets associated with the VPC."
}

variable "bulk_loader_s3_bucket_name" {
  type        = string
  description = "Name of the S3 bucket storing Neptune bulk load files."
}

variable "graph_date" {
  type        = string
  description = "Date associated with this graph instance (YYYY-MM-DD, or 'dev'), incorporated into the namespace."

  validation {
    condition     = var.graph_date == "dev" || can(formatdate("YYYY-MM-DD", "${var.graph_date}T00:00:00Z"))
    error_message = "graph_date must be a date (YYYY-MM-DD) or 'dev'."
  }
}

variable "skip_final_snapshot" {
  type        = bool
  description = "Drop the cluster without a final snapshot on destroy. Only for a cluster being decommissioned."
  default     = false
}

