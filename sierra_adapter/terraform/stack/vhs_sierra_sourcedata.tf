module "vhs_sierra" {
  source             = "git::github.com/wellcomecollection/terraform-aws-vhs.git//hash-store?ref=v3.4.3"
  bucket_name_prefix = "wellcomecollection-vhs-"
  table_name_prefix  = "vhs-"
  name               = "sierra-${local.namespace_hyphen}"
  tags               = {}
  read_principals    = ["arn:aws:iam::269807742353:root"]
}
