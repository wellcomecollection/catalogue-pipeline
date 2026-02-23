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

