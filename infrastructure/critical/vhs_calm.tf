module "vhs_calm_sourcedata" {
  source     = "./vhs"
  name       = "calm"

  read_principals = [
    "arn:aws:iam::269807742353:root"
  ]
}
