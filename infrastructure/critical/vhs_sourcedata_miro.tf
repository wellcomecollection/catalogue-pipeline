module "vhs_miro" {
  source = "./vhs"
  name   = "sourcedata-miro"

  read_principals = [
    "arn:aws:iam::269807742353:root",
  ]
}
