# Shared across pipeline dates; the id minter creates its namespace and tables on first use.
resource "aws_s3tables_table_bucket" "catalogue_pipeline" {
  name = "wellcomecollection-platform-catalogue-pipeline"
  encryption_configuration = {
    sse_algorithm = "AES256"
    kms_key_arn   = null
  }
}
