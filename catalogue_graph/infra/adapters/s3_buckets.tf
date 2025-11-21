resource "aws_s3_bucket" "ebsco_adapter" {
  bucket = "wellcomecollection-platform-ebsco-adapter"

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "ebsco_adapter" {
  bucket = aws_s3_bucket.ebsco_adapter.id

  rule {
    id     = "delete_objects_after_6_months"
    status = "Enabled"

    expiration {
      days = 180
    }
  }
}
