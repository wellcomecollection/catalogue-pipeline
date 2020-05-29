module "vhs_sierra_items" {
  source             = "git::github.com/wellcomecollection/terraform-aws-vhs.git//hash-store?ref=v2.0.0"
  bucket_name_prefix = "wellcomecollection-vhs-"
  table_name_prefix  = "vhs-"
  name               = "vhs_sierra_items-${local.namespace_hyphen}"
}
