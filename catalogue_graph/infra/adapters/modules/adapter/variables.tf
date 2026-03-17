variable "namespace" {
  type        = string
  description = "Namespace associated with the adapter (e.g. ebsco, axiell, folio)"
}

variable "s3_bucket_name" {
  type        = string
  description = "Name of S3 bucket associated with the adapter"
}


variable "repository_url" {
  type        = string
  description = "ECR repository URL for the Lambda function image"
}

variable "schedule_expression" {
  type        = string
  description = "Schedule pattern for adapter runs"
}
