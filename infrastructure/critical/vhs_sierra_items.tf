module "vhs_sierra_items" {
  source     = "./vhs"
  name       = "sourcedata-sierra-items"
  account_id = "${data.aws_caller_identity.current.account_id}"

  s3_cross_account_access_accounts = ["269807742353"]
}
