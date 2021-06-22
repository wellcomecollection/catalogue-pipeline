locals {
  table_name = "tai-adapter-store"
}

resource "aws_dynamodb_table" "tei_adapter_table" {
  name     = local.table_name
  hash_key = "id"

  attribute {
    name = "id"
    type = "S"
  }

  billing_mode = "PAY_PER_REQUEST"

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = local.table_name
  }
}

data "aws_iam_policy_document" "tei_dynamo_full_access_policy" {
  statement {
    actions = [
      "dynamodb:*",
    ]

    resources = [
      aws_dynamodb_table.tei_adapter_table.arn,
      "${aws_dynamodb_table.tei_adapter_table.arn}/*",
    ]
  }
}

data "aws_iam_policy_document" "tei_dynamo_read_policy" {
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
      aws_dynamodb_table.tei_adapter_table.arn,
    ]
  }
}

