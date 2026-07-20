# S3 bucket for the JSON run reports produced by sync operations.

resource "aws_s3_bucket" "axiell_folio_sync_manifests" {
  bucket = var.manifest_bucket_name

  lifecycle {
    prevent_destroy = false
  }
}

# Expire manifests after the configured retention window.
resource "aws_s3_bucket_lifecycle_configuration" "axiell_folio_sync_manifests" {
  bucket = aws_s3_bucket.axiell_folio_sync_manifests.id

  rule {
    id     = "expire-manifests"
    status = "Enabled"

    filter {
      prefix = "manifests/"
    }

    expiration {
      days = var.manifest_retention_days
    }
  }
}
