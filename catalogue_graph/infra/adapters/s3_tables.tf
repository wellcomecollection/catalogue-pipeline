resource "aws_s3tables_table_bucket" "table_bucket" {
  name = "wellcomecollection-platform-ebsco-adapter"
  encryption_configuration = {
    sse_algorithm = "AES256"
    kms_key_arn = null
  }
}

resource "aws_s3tables_namespace" "namespace" {
  namespace        = "wellcomecollection_catalogue"
  table_bucket_arn = aws_s3tables_table_bucket.table_bucket.arn
}

resource "aws_s3tables_table_bucket" "axiell_table_bucket" {
  name = "wellcomecollection-platform-axiell-adapter"
  encryption_configuration = {
    sse_algorithm = "AES256"
    kms_key_arn = null
  }
}

resource "aws_s3tables_namespace" "axiell_namespace" {
  namespace        = "wellcomecollection_catalogue"
  table_bucket_arn = aws_s3tables_table_bucket.axiell_table_bucket.arn
}
