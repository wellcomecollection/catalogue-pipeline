resource "aws_s3_bucket" "sierra_adapter" {
  bucket = "wellcomecollection-platform-${local.namespace_hyphen}"

  lifecycle {
    prevent_destroy = false
  }

  lifecycle_rule {
    id      = "expire_old_items"
    prefix  = "records_items/"
    enabled = true

    expiration {
      days = 7
    }
  }

  lifecycle_rule {
    id      = "expire_old_bibs"
    prefix  = "records_bibs/"
    enabled = true

    expiration {
      days = 7
    }
  }

  lifecycle_rule {
    id      = "expire_old_holdings"
    prefix  = "records_holdings/"
    enabled = true

    expiration {
      days = 7
    }
  }

  lifecycle_rule {
    id      = "expire_old_orders"
    prefix  = "records_orders/"
    enabled = true

    expiration {
      days = 7
    }
  }
}
