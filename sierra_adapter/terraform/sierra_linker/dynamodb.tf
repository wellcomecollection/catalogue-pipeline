resource "aws_dynamodb_table" "links" {
  name     = "sourcedata-sierra-${var.resource_type}-${replace(var.namespace, "sierra-adapter-", "")}"
  hash_key = "id"

  attribute {
    name = "id"
    type = "S"
  }

  stream_enabled = false
  billing_mode   = "PAY_PER_REQUEST"
}
