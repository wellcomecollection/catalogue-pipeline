# IAM Policy Documents for S3 Tables Iceberg access

data "aws_iam_policy_document" "iceberg_write" {
  statement {
    actions = [
      "lakeformation:GetDataAccess"
    ]

    resources = ["*"]
  }

  statement {
    actions = [
      "glue:GetCatalog",
      "glue:CreateDatabase",
      "glue:CreateTable",
      "glue:GetTable"
    ]

    resources = [
      "arn:aws:glue:eu-west-1:760097843905:catalog",
      "arn:aws:glue:eu-west-1:760097843905:catalog/*",
      "arn:aws:glue:eu-west-1:760097843905:database/s3tablescatalog/wellcomecollection-platform-ebsco-adapter/wellcomecollection_catalogue"
    ]
  }

    statement {
    actions = [
      "glue:CreateTable",
      "glue:GetTable",
    ]

    resources = [
      "arn:aws:glue:eu-west-1:760097843905:table/s3tablescatalog/wellcomecollection-platform-ebsco-adapter/wellcomecollection_catalogue/ebsco_adapter_table"
    ]
  }
}


# Policy for reading from the EBSCO adapter S3 bucket
data "aws_iam_policy_document" "s3_read" {
  statement {
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]

    resources = [
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter",
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter/*"
    ]
  }
}

# Create the Glue database that the Lambda will use
resource "aws_glue_catalog_database" "wellcomecollection_catalogue" {
  name        = "wellcomecollection_catalogue"
  description = "Database for Wellcome Collection catalogue data from EBSCO adapter"
}
