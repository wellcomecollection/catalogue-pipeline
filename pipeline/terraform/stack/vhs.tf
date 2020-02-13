module "vhs_recorder" {
  source = "git::github.com/wellcomecollection/terraform-aws-vhs.git?ref=v1.0.0"

  name = "${local.namespace_hyphen}-recorder"
}
