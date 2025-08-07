# IAM Policy Documents for S3 Tables Iceberg access

# Policy for writing to S3 Tables Iceberg table
data "aws_iam_policy_document" "iceberg_write" {
  # Glue Data Catalog permissions for S3 Tables catalog access
  statement {
    actions = [
      "glue:GetCatalog",
      "glue:GetDatabase",
      "glue:GetTable",
      "glue:CreateDatabase"
    ]

    resources = [
      "arn:aws:glue:eu-west-1:760097843905:catalog",
      "arn:aws:glue:eu-west-1:760097843905:catalog/*",
      "arn:aws:glue:eu-west-1:760097843905:database/s3tablescatalog/${aws_s3tables_table_bucket.table_bucket.name}/*"
    ]
  }

  # Lake Formation permissions for credential federation
  statement {
    actions = [
      "lakeformation:GetDataAccess",
      "lakeformation:CreateDatabase"
    ]

    resources = ["*"]
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
