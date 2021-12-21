module "vhs_miro" {
  source = "github.com/wellcomecollection/terraform-aws-vhs.git//single-version-store?ref=v4.2.0"

  name        = "sourcedata-miro"
  table_name  = "vhs-sourcedata-miro"
  bucket_name = "wellcomecollection-vhs-sourcedata-miro"

  read_principals = local.read_principles
}

resource "aws_s3_bucket_policy" "allow_cross_account_miro_vhs_read" {
  bucket = module.vhs_miro.bucket_name
  policy = data.aws_iam_policy_document.allow_miro_s3_read.json
}

data "aws_iam_policy_document" "allow_miro_s3_read" {
  statement {
    principals {
      identifiers = local.read_principles
      type        = "AWS"
    }

    actions = [
      "s3:Get*",
      "s3:List*",
    ]

    resources = [
      "arn:aws:s3:::${module.vhs_miro.bucket_name}/*",
      "arn:aws:s3:::${module.vhs_miro.bucket_name}",
    ]
  }
}
