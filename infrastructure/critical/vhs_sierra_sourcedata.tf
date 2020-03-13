module "vhs_sierra" {
  source = "./modules/vhs"
  name   = "sourcedata-sierra"

  read_principals = local.read_principles
}

