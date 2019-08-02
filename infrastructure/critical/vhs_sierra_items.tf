module "vhs_sierra_items" {
  source = "./vhs"
  name   = "sourcedata-sierra-items"

  read_principals = ["${local.read_principles}"]
}
