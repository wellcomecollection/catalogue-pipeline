module "vhs_miro_migration" {
  source = "./vhs"
  name   = "miro-migration"

  read_principals = ["${local.read_principles}"]
}
