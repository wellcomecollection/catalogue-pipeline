module "vhs_sierra_items" {
  source = "./modules/vhs"
  name   = "sourcedata-sierra-items"

  read_principles = local.read_principles
}

