module "vhs_calm" {
  source = "./modules/vhs"
  name   = "sourcedata-calm"

  read_principals = local.read_principles
}

