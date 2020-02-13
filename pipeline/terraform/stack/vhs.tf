module "vhs_recorder" {
  source = "git::https://github.com/wellcomecollection/terraform-aws-vhs.git?ref=v19.7.2"

  name = "${local.namespace_hyphen}-recorder"
}
