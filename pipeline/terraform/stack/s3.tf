resource "aws_s3_bucket" "messages" {
  bucket = "wellcomecollection-${local.namespace_hyphen}-messages"
  acl    = "private"

  # Normally S3 buckets have prevent_destroy = true
  # but these are transient between stacks, so we allow it.
  force_destroy = true

  lifecycle_rule {
    id      = "expire_messages"
    enabled = true

    expiration {
      // SQS messages are kept in queues for 4 days
      // expiration of messages in s3 should be the same
      days = 4
    }
  }
}
