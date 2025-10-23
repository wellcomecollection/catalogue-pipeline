data "aws_vpc" "vpc" {
  id = var.vpc_id
}

data "aws_s3_bucket" "bulk_loader_bucket" {
  bucket = var.bulk_loader_s3_bucket_name
}
