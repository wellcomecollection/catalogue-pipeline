data "aws_iam_policy_document" "read_write_dynamo" {
  statement {
    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]

    resources = [
      aws_dynamodb_table.links.arn,
      "${aws_dynamodb_table.links.arn}/*",
    ]
  }
}
