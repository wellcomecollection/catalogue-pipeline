module "vhs_sierra" {
  source = "./modules/vhs"
  name   = "sourcedata-sierra"

  read_principles = local.read_principles
}

