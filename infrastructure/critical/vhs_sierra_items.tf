module "vhs_sierra_items" {
  source = "./vhs"
  name   = "sourcedata-sierra-items"

  read_principals = [
    "arn:aws:iam::269807742353:root",
  ]
}
