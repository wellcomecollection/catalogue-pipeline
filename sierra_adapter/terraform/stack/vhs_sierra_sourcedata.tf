module "vhs_sierra" {
  source = "github.com/wellcomecollection/terraform-aws-vhs.git//single-version-store?ref=v4.0.5"

  bucket_name_prefix = "wellcomecollection-vhs-"
  table_name_prefix  = "vhs-"
  name               = "sierra-${local.namespace_hyphen}"
}

resource "aws_dynamodb_table" "deleted_records" {
  name     = "${module.vhs_sierra.table_name}-deleted"
  hash_key = "id"

  billing_mode = "PAY_PER_REQUEST"

  attribute {
    name = "id"
    type = "S"
  }
}
