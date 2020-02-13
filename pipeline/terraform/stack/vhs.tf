module "vhs_recorder" {
  source             = "git::github.com/wellcomecollection/terraform-aws-vhs.git?ref=v1.0.0"
  bucket_name_prefix = "wellcomecollection-vhs-"
  table_name_prefix  = "vhs-"
  name               = "${local.namespace_hyphen}-recorder"
}
