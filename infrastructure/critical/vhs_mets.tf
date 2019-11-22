module "vhs_mets" {
  source = "./vhs"
  name   = "mets"

  read_principals = ["${local.read_principles}"]
}
