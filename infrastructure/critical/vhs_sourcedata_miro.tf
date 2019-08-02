module "vhs_miro" {
  source = "./vhs"
  name   = "sourcedata-miro"

  read_principals = ["${local.read_principles}"]
}
