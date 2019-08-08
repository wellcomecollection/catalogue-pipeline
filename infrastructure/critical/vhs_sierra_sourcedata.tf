module "vhs_sierra" {
  source = "./vhs"
  name   = "sourcedata-sierra"

  read_principals = ["${local.read_principles}"]
}
