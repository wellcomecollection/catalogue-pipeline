module "vhs" {
  source             = "git::github.com/wellcomecollection/terraform-aws-vhs.git//hash-store?ref=v3.4.3"
  bucket_name_prefix = "wellcomecollection-vhs-"
  table_name_prefix  = "vhs-"
  name               = local.namespace
  tags               = {}
  read_principals    = ["arn:aws:iam::269807742353:root"]
}

resource "aws_iam_role_policy" "vhs_readwrite" {
  role   = module.worker.task_role_name
  policy = module.vhs.full_access_policy
}
