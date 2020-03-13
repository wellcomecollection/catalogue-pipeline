module "vhs_miro_migration" {
  source = "./modules/vhs"
  name   = "miro-migration"

  read_principals = local.read_principles
}
