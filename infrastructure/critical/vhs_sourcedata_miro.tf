module "vhs_miro" {
  source = "./modules/vhs"
  name   = "sourcedata-miro"

  read_principals = local.read_principles
}
