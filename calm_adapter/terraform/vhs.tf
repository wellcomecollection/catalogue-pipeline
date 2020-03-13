module "vhs" {
  source             = "git::github.com/wellcomecollection/terraform-aws-vhs.git//hash-store?ref=f62e0544687a7361810a14ef78a5e198cfc5d365"
  bucket_name_prefix = "wellcomecollection-vhs-"
  table_name_prefix  = "vhs-"
  name               = local.namespace
}

resource "aws_iam_role_policy" "vhs_readwrite" {
  role   = module.task_definition.task_role_name
  policy = module.vhs.full_access_policy
}
