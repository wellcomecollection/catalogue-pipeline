module "vhs_miro" {
  source = "./modules/vhs"
  name   = "sourcedata-miro"

  read_principles = local.read_principles
}
