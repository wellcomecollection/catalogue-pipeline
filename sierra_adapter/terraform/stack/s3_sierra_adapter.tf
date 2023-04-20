resource "aws_s3_bucket" "sierra_adapter" {
  bucket = "wellcomecollection-platform-${local.namespace_hyphen}"

  lifecycle {
    prevent_destroy = false
  }


}

resource "aws_s3_bucket_lifecycle_configuration" "sierra_adapter" {
  bucket = aws_s3_bucket.sierra_adapter.id

  rule {
    id     = "expire_old_items"
    status = "Enabled"

    filter {
      prefix = "records_items/"
    }

    expiration {
      days = 7
    }
  }

  rule {
    id     = "expire_old_bibs"
    status = "Enabled"

    filter {
      prefix = "records_bibs/"
    }

    expiration {
      days = 7
    }
  }

  rule {
    id     = "expire_old_holdings"
    status = "Enabled"

    filter {
      prefix = "records_holdings/"
    }

    expiration {
      days = 7
    }
  }

  rule {
    id = "expire_old_orders"

    status = "Enabled"

    filter {
      prefix = "records_orders/"
    }

    expiration {
      days = 7
    }
  }
}
