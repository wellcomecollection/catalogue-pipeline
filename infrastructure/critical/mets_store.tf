
resource "aws_dynamodb_table" "mets_adapter_table" {
  name     = "mets-adapter-store"
  hash_key = "id"

  attribute {
    name = "id"
    type = "S"
  }

  billing_mode = "PAY_PER_REQUEST"

  lifecycle {
    prevent_destroy = true
  }
}

data "aws_iam_policy_document" "mets_dynamo_full_access_policy" {
  statement {
    actions = [
      "dynamodb:*",
    ]

    resources = [
      "${aws_dynamodb_table.mets_adapter_table.arn}",

      # Allow access to the GSIs on the table
      "${aws_dynamodb_table.mets_adapter_table.arn}/*",
    ]
  }
}

data "aws_iam_policy_document" "mets_dynamo_read_policy" {
  # This is based on the AmazonDynamoDBReadOnlyAccess
  statement {
    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:DescribeTable",
      "dynamodb:GetItem",
      "dynamodb:ListTables",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]

    resources = [
      "${aws_dynamodb_table.mets_adapter_table.arn}",
    ]
  }
}
