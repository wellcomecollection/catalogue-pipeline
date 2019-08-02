module "vhs_sierra" {
  source = "./vhs"
  name   = "sourcedata-sierra"

  read_principals = [
    "arn:aws:iam::269807742353:root"
  ]
}