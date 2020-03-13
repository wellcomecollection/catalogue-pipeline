module "vhs_calm" {
  source = "./modules/vhs"
  name   = "sourcedata-calm"

  read_principles = local.read_principles
}

