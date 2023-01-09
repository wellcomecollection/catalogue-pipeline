module "vhs" {
  source             = "git::github.com/wellcomecollection/terraform-aws-vhs.git//single-version-store?ref=v4.0.5"
  bucket_name_prefix = "wellcomecollection-vhs-"
  table_name_prefix  = "vhs-"
  name               = local.namespace
}

resource "aws_iam_role_policy" "vhs_adapter_readwrite" {
  role   = module.calm_adapter.task_role_name
  policy = module.vhs.full_access_policy
}

resource "aws_iam_role_policy" "vhs_deletion_checker_dynamo_update" {
  role   = module.calm_deletion_checker.task_role_name
  policy = module.vhs.dynamodb_update_policy.json
}

resource "aws_iam_role_policy" "indexer_read_from_vhs" {
  role   = module.calm_indexer.task_role_name
  policy = module.vhs.read_policy
}

data "aws_iam_policy_document" "vhs_dynamo_read_policy" {
  statement {
    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:DescribeTable",
      "dynamodb:GetItem",
      "dynamodb:ListTables",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]

    resources = [module.vhs.table_arn]
  }
}

resource "aws_iam_role_policy" "vhs_deletion_check_initiator_dynamo_read" {
  role   = module.deletion_check_initiator_lambda.role_name
  policy = data.aws_iam_policy_document.vhs_dynamo_read_policy.json
}
