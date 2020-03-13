module "vhs_miro_migration" {
  source = "./modules/vhs"
  name   = "miro-migration"

  read_principles = local.read_principles
}
