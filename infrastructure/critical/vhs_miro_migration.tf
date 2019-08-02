module "vhs_miro_migration" {
  source = "./vhs"
  name   = "miro-migration"

  read_principals = [
    "arn:aws:iam::269807742353:root"
  ]
}
