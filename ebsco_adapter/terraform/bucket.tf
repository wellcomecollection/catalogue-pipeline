resource "aws_s3_bucket" "ebsco_adapter" {
  bucket = "wellcomecollection-platform-${local.namespace}"

  lifecycle {
    prevent_destroy = false
  }
}

