resource "aws_s3tables_table_bucket" "table_bucket" {
  name = "wellcomecollection-platform-ebsco-adapter"
}

resource "aws_s3tables_namespace" "namespace" {
  namespace        = "wellcomecollection_catalogue"
  table_bucket_arn = aws_s3tables_table_bucket.table_bucket.arn
}

resource "aws_s3tables_table" "iceberg_table" {
  name             = "ebsco_adapter_table"
  namespace        = aws_s3tables_namespace.namespace.namespace
  table_bucket_arn = aws_s3tables_table_bucket.table_bucket.arn
  format           = "ICEBERG"
}

