module "vhs_recorder" {
  source = "git::https://github.com/wellcometrust/terraform.git//vhs/modules/vhs?ref=v19.7.2"

  name = "${local.namespace_hyphen}-recorder"
}
