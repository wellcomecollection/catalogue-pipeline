module "vhs_calm_sourcedata" {
  source = "./vhs"
  name   = "calm"

  read_principals = ["${local.read_principles}"]
}
