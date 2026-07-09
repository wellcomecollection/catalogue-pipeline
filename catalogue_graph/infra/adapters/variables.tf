# Provided via terraform.tfvars (gitignored). See terraform.tfvars.example.
# Used by the axiell_folio_sync module.

variable "okapi_url" {
  description = "FOLIO OKAPI base URL for the Axiell → FOLIO sync"
  type        = string
}

variable "okapi_tenant" {
  description = "FOLIO OKAPI tenant ID for the Axiell → FOLIO sync"
  type        = string
}

variable "s3_table_bucket_arn" {
  description = "ARN of the Axiell adapter S3 Tables bucket (bucket scope), used to scope the sync's IAM read access"
  type        = string
}
