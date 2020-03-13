data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id

  table_name  = "${var.table_name_prefix}${var.name}"
  bucket_name = "${var.bucket_name_prefix}${lower(var.name)}"

  table_arn  = "arn:aws:dynamodb:${var.aws_region}:${local.account_id}:table/${local.table_name}"
  bucket_arn = "arn:aws:s3:::${local.bucket_name}"
}
