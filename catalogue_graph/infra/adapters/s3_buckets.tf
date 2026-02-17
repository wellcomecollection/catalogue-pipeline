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

resource "aws_s3_bucket" "axiell_adapter" {
  bucket = "wellcomecollection-platform-axiell-adapter"

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "axiell_adapter" {
  bucket = aws_s3_bucket.axiell_adapter.id

  rule {
    id     = "delete_objects_after_6_months"
    status = "Enabled"

    expiration {
      days = 180
    }
  }
}

resource "aws_s3_bucket" "folio_adapter" {
  bucket = "wellcomecollection-platform-folio-adapter"

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "folio_adapter" {
  bucket = aws_s3_bucket.folio_adapter.id

  rule {
    id     = "delete_objects_after_6_months"
    status = "Enabled"

    expiration {
      days = 180
    }
  }
}
