data "archive_file" "sierra_reader" {
  type        = "zip"
  source_dir  = "${path.module}/../sierra_reader_new"
  output_path = "${path.module}/sierra_reader.zip"
}

resource "aws_s3_object" "sierra_reader" {
  bucket = var.infra_bucket
  key    = "lambdas/sierra_adapter/sierra_reader.zip"
  source = data.archive_file.sierra_reader.output_path
  etag   = filemd5(data.archive_file.sierra_reader.output_path)
}

locals {
  sierra_reader_zip = {
    bucket = aws_s3_object.sierra_reader.bucket
    key    = aws_s3_object.sierra_reader.key
  }
}
