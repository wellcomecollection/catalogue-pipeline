module "vhs_goobi_mets" {
  source = "./vhs"
  name   = "goobi-mets"

  read_principals = [
    "arn:aws:iam::269807742353:root",
  ]
}
