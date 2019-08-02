module "vhs_goobi_mets" {
  source = "./vhs"
  name   = "goobi-mets"

  read_principals = ["${local.read_principles}"]
}
