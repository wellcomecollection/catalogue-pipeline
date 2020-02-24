module "vhs_recorder" {
  source             = "git::github.com/wellcomecollection/terraform-aws-vhs.git//hash-store?ref=f62e0544687a7361810a14ef78a5e198cfc5d365"
  bucket_name_prefix = "wellcomecollection-vhs-"
  table_name_prefix  = "vhs-"
  name               = "${local.namespace_hyphen}-recorder"
}
