module "vhs_miro_migration" {
  source     = "./vhs"
  name       = "miro-migration"
  account_id = "${data.aws_caller_identity.current.account_id}"

  s3_cross_account_access_accounts = ["269807742353"]
}
